package com.company.crm.activity.service;

import com.company.crm.activity.ActivityType;
import com.company.crm.activity.dto.ActivityRequest;
import com.company.crm.activity.dto.ActivityView;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

public interface ActivityService {

  Page<ActivityView> searchActivities(
      ActivityType type, String search, int page, int size, Authentication auth);

  Page<ActivityView> listByClient(UUID clientId, int page, int size, Authentication auth);

  ActivityView createActivity(UUID clientId, ActivityRequest request, Authentication auth);

  ActivityView getActivity(UUID id, Authentication auth);
}
