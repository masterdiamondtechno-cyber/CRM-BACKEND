package com.company.crm.common;

public record ApiResponse<T>(String status, String message, T data) {
  public static <T> ApiResponse<T> ok(String m, T d) {
    return new ApiResponse<>("success", m, d);
  }

  public static <T> ApiResponse<T> ok(T d) {
    return new ApiResponse<>("success", "Operation successful", d);
  }

  public static <T> ApiResponse<T> error(String m) {
    return new ApiResponse<>("error", m, null);
  }
}
