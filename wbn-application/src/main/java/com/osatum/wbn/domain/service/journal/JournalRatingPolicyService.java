package com.osatum.wbn.domain.service.journal;

import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class JournalRatingPolicyService {

    public RatingStatus getRatingStatus(String state) {
        Optional<RatingIcvStatus> oStatus = RatingIcvStatus.fromValue(state);
        if (oStatus.isEmpty()) {
            return RatingStatus.INDEXED;
        }
        if (RatingIcvStatus.PUBLISHED.equals(oStatus.get())) {
            return RatingStatus.INDEXED;
        }
        return RatingStatus.NOT_INDEXED;

    }
}
