package com.campus.repair.module.auth.dto;

/**
 * 修改本人资料：campus 仅报修人可改（维修工服务校区由后勤维护）。
 */
public record UpdateMeReq(String phone, String department, String address, String campus) {
}
