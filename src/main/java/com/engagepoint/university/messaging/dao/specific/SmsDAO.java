package com.engagepoint.university.messaging.dao.specific;

import com.engagepoint.university.messaging.dao.generic.GenericDAO;
import com.engagepoint.university.messaging.dto.SmsDTO;

import java.util.List;

public interface SmsDAO extends GenericDAO<SmsDTO> {
    public List<SmsDTO> getSmsBySender(String sender);

    public void saveSmsDAO(SmsDTO smsDTO);

    public void deleteIdList(List<Long> idList);
}
