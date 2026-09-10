package com.example.siteplatform.siteaccess.service;

import com.example.siteplatform.siteaccess.mapper.GuardVisitorMatchMapper;
import com.example.siteplatform.siteaccess.vo.PublicGuardMatchedPassVO;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GuardVisitorMatchingService {
    private final GuardVisitorMatchMapper mapper;
    private final VisitorSessionService sessions;
    private final VisitorDataCryptoService crypto;

    public GuardVisitorMatchingService(GuardVisitorMatchMapper mapper, VisitorSessionService sessions,
                                       VisitorDataCryptoService crypto) {
        this.mapper = mapper;
        this.sessions = sessions;
        this.crypto = crypto;
    }

    public List<PublicGuardMatchedPassVO> find(VisitorSessionService.VisitorSessionContext context, LocalDateTime now) {
        var result = new ArrayList<>(mapper.single(context.projectId(), context.appId(),
                VisitorIdentitySupport.hash("single-registration", context, crypto, sessions), now));
        result.addAll(mapper.meeting(context.projectId(), context.appId(),
                VisitorIdentitySupport.hash("meeting-registration", context, crypto, sessions), now));
        for (var pass : result) {
            pass.setPeople("SINGLE".equals(pass.getSourceType())
                    ? mapper.singlePeople(pass.getSourceId(), context.projectId())
                    : mapper.meetingPeople(pass.getSourceId(), context.projectId()));
        }
        return result;
    }
}
