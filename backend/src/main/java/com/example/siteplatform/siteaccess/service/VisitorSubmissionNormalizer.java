package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.siteaccess.dto.SiteVisitPersonRequest;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Keeps invitation and guard registration validation behavior identical. */
public final class VisitorSubmissionNormalizer {
    public static final String PERSON_CONTACT = "CONTACT";
    public static final String PERSON_COMPANION = "COMPANION";
    public static final String TRAVEL_DRIVING = "DRIVING";
    public static final String TRAVEL_OTHER = "OTHER";
    private static final int MAX_VISITORS = 50;

    private VisitorSubmissionNormalizer() {
    }

    public static Submission normalize(String company, String contactName, String contactPhone,
                                       List<SiteVisitPersonRequest> companions,
                                       String travelMode, String vehiclePlate, String visitorRemark) {
        String normalizedCompany = requiredText(company, 200, "外访单位");
        String normalizedContactName = requiredText(contactName, 50, "主联系人姓名");
        String normalizedPhone = requiredText(contactPhone, 11, "主联系人手机号");
        if (!normalizedPhone.matches("^1[3-9]\\d{9}$")) throw new BusinessException("手机号格式不正确");
        String normalizedTravelMode = requiredText(travelMode, 20, "出行方式").toUpperCase(Locale.ROOT);
        if (!Set.of(TRAVEL_DRIVING, TRAVEL_OTHER).contains(normalizedTravelMode)) {
            throw new BusinessException("出行方式不正确");
        }
        String normalizedPlate = normalizePlate(vehiclePlate);
        if (TRAVEL_DRIVING.equals(normalizedTravelMode) && !StringUtils.hasText(normalizedPlate)) {
            throw new BusinessException("驾车来访必须填写车牌号");
        }
        if (TRAVEL_OTHER.equals(normalizedTravelMode)) normalizedPlate = null;

        List<Person> people = new ArrayList<>();
        people.add(new Person(PERSON_CONTACT, normalizedCompany, normalizedContactName, normalizedPhone));
        for (SiteVisitPersonRequest companion : companions == null ? List.<SiteVisitPersonRequest>of() : companions) {
            if (companion == null) throw new BusinessException("同行人员信息不能为空");
            String companionCompany = optionalText(companion.getPersonCompany(), 200, "同行人员单位");
            String companionName = optionalText(companion.getPersonName(), 50, "同行人员姓名");
            String companionPhone = optionalText(companion.getPersonPhone(), 11, "同行人员手机号");
            if (companionCompany == null && companionName == null && companionPhone == null) continue;
            if (companionPhone != null && !companionPhone.matches("^1[3-9]\\d{9}$")) {
                throw new BusinessException("同行人员手机号格式不正确");
            }
            people.add(new Person(PERSON_COMPANION, companionCompany, companionName, companionPhone));
        }
        if (people.size() > MAX_VISITORS) throw new BusinessException("一次来访最多登记50名人员");
        return new Submission(normalizedCompany, normalizedContactName, normalizedPhone,
                normalizedTravelMode, normalizedPlate, optionalText(visitorRemark, 500, "外访备注"),
                List.copyOf(people));
    }

    public static String requiredText(String value, int max, String field) {
        String result = trimToNull(value);
        if (result == null) throw new BusinessException(field + "不能为空");
        if (result.length() > max) throw new BusinessException(field + "不能超过" + max + "个字符");
        return result;
    }

    public static String optionalText(String value, int max, String field) {
        String result = trimToNull(value);
        if (result != null && result.length() > max) throw new BusinessException(field + "不能超过" + max + "个字符");
        return result;
    }

    private static String normalizePlate(String raw) {
        String value = trimToNull(raw);
        if (value == null) return null;
        value = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (value.length() < 2 || value.length() > 20) throw new BusinessException("车牌号长度不正确");
        return value;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record Person(String personType, String personCompany, String personName, String personPhone) {
    }

    public record Submission(String visitorCompany, String contactName, String contactPhone,
                             String travelMode, String vehiclePlate, String visitorRemark,
                             List<Person> people) {
    }
}
