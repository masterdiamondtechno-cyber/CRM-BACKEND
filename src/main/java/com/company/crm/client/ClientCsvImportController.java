package com.company.crm.client;
import com.company.crm.common.ApiResponse;
import com.company.crm.security.TenantContext;
import com.company.crm.security.TenantMembershipService;
import com.company.crm.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientCsvImportController {
    static final int MAX_FILE_BYTES = 2 * 1024 * 1024, MAX_ROWS = 500, MAX_FIELD = 10_000;
    private final ClientRepository clients;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final TenantMembershipService memberships;
    public ClientCsvImportController(ClientRepository c, UserRepository u, JdbcTemplate j, TenantMembershipService m) {
        clients=c; users=u; jdbc=j; memberships=m;
    }
    public record ImportResult(int imported, int skippedDuplicates, List<Integer> duplicateRows) {}
    private record Row(int number, String company, String industry, String website, String email,
                       String phone, String address, String notes, ClientStatus status) {}

    @PostMapping(value="/import", consumes="multipart/form-data")
    @Transactional
    public ApiResponse<ImportResult> importCsv(@RequestPart("file") MultipartFile file, Authentication auth) {
        if (file.isEmpty() || file.getSize()>MAX_FILE_BYTES) throw bad("CSV must be non-empty and at most 2 MB.");
        List<List<String>> records;
        try { records=parse(new String(file.getBytes(), StandardCharsets.UTF_8)); }
        catch(IOException e) { throw bad("Could not read CSV file."); }
        if(records.size()<2) throw bad("CSV needs a header and at least one row.");
        if(records.size()-1>MAX_ROWS) throw bad("CSV is limited to 500 rows.");
        Map<String,Integer> cols=header(records.get(0));
        int companyCol=cols.containsKey("company")?cols.get("company"):cols.getOrDefault("companyname",-1);
        if(companyCol<0) throw bad("CSV needs a Company or companyName column.");
        List<Row> parsed=new ArrayList<>(); Set<String> batch=new HashSet<>(); List<Integer> duplicates=new ArrayList<>();
        for(int i=1;i<records.size();i++){
            List<String> cells=records.get(i); if(cells.size()==1&&cells.get(0).isBlank()) continue;
            int line=i+1; String company=cell(cells,companyCol).trim();
            if(company.isEmpty()||company.length()>200) throw bad("Row "+line+": company is required (max 200 chars).");
            if(!batch.add(norm(company))){duplicates.add(line);continue;}
            String email=val(cells,cols,"email");
            if(email!=null&&(email.length()>255||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")))
                throw bad("Row "+line+": invalid email.");
            parsed.add(new Row(line,company,limited(cells,cols,"industry",100,line),
                limited(cells,cols,"website",255,line),email,limited(cells,cols,"phone",30,line),
                limited(cells,cols,"address",MAX_FIELD,line),limited(cells,cols,"notes",MAX_FIELD,line),
                status(val(cells,cols,"status"),line)));
        }
        UUID tenant=TenantContext.requireTenantId();
        Set<String> existing=new HashSet<>(jdbc.queryForList(
            "select lower(trim(company_name)) from clients where tenant_id=? and archived_at is null",String.class,tenant));
        var owner=users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        memberships.requireActiveMember(owner.getId());
        List<Client> toSave=new ArrayList<>();
        for(Row r:parsed){
            if(!existing.add(norm(r.company()))){duplicates.add(r.number());continue;}
            // Tenant and owner are always taken from authenticated server context.
            toSave.add(new Client(r.company(),r.industry(),r.website(),r.email(),r.phone(),r.address(),r.notes(),owner,r.status()));
        }
        clients.saveAll(toSave);
        duplicates.sort(Integer::compareTo);
        return ApiResponse.ok("Client CSV imported",new ImportResult(toSave.size(),duplicates.size(),List.copyOf(duplicates)));
    }
    static List<List<String>> parse(String csv){
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>(); StringBuilder f=new StringBuilder();
        boolean quote=false,closed=false;
        for(int i=0;i<csv.length();i++){
            char c=csv.charAt(i);
            if(quote){if(c=='"'&&i+1<csv.length()&&csv.charAt(i+1)=='"'){f.append('"');i++;}
                else if(c=='"'){quote=false;closed=true;}else f.append(c);}
            else if(closed){if(c==','){add(row,f);closed=false;}
                else if(c=='\n'||c=='\r'){add(row,f);push(rows,row);closed=false;if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;}
                else if(c!=' '&&c!='\t')throw bad("Malformed quoted CSV field.");}
            else if(c=='"'&&f.length()==0)quote=true;
            else if(c=='"')throw bad("Quote inside unquoted CSV field.");
            else if(c==',')add(row,f);
            else if(c=='\n'||c=='\r'){add(row,f);push(rows,row);if(c=='\r'&&i+1<csv.length()&&csv.charAt(i+1)=='\n')i++;}
            else f.append(c);
            if(f.length()>MAX_FIELD)throw bad("CSV field exceeds 10,000 characters.");
        }
        if(quote)throw bad("Unterminated quoted CSV field.");
        if(f.length()!=0||!row.isEmpty()||closed){add(row,f);push(rows,row);}
        return rows;
    }
    private static void add(List<String> row,StringBuilder f){row.add(f.toString());f.setLength(0);}
    private static void push(List<List<String>> rows,List<String> row){rows.add(List.copyOf(row));row.clear();if(rows.size()>MAX_ROWS+1)throw bad("CSV is limited to 500 rows.");}
    static Map<String,Integer> header(List<String> row){
        Map<String,Integer> m=new HashMap<>();
        for(int i=0;i<row.size();i++){String k=row.get(i).replace(String.valueOf((char)0xFEFF),"").trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+","");
            if(!k.isEmpty()&&m.putIfAbsent(k,i)!=null)throw bad("Duplicate CSV header.");}
        return m;
    }
    private static String cell(List<String> r,int i){return i<r.size()?r.get(i):"";}
    private static String val(List<String> r,Map<String,Integer> m,String k){Integer i=m.get(k);if(i==null)return null;String v=cell(r,i).trim();return v.isEmpty()?null:v;}
    private static String limited(List<String> r,Map<String,Integer> m,String k,int max,int line){String v=val(r,m,k);if(v!=null&&v.length()>max)throw bad("Row "+line+": "+k+" is too long.");return v;}
    private static ClientStatus status(String v,int line){if(v==null)return ClientStatus.PROSPECT;try{return ClientStatus.valueOf(v.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){throw bad("Row "+line+": invalid client status.");}}
    private static String norm(String s){return s.trim().toLowerCase(Locale.ROOT);}
    private static ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
}

