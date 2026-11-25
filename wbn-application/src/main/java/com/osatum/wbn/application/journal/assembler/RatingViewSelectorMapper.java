package com.osatum.wbn.application.journal.assembler;


import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.application.journal.view.RatingView;
import com.osatum.wbn.domain.model.journal.Rating;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import com.osatum.wbn.domain.service.journal.JournalRatingPolicyService;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.Context;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mapper(componentModel = "spring", uses = {ConfigurationService.class})
public abstract class RatingViewSelectorMapper {

    @Autowired
    private ConfigurationService configurationService;


    @Named("icv")
    List<RatingView> icv(Set<Rating> ratings, @Context Integer surveyYear) {
        if (ratings == null) return new ArrayList<>();

        List<RatingView> result = new ArrayList<>();

        Integer currentYear = selectedYear(surveyYear);

        Optional<Integer> oFrom = ratings.stream()
                .filter(x -> Rating.ICV_RATING_CODE.equals(x.getRatingCode()))
                .map(Rating::getYear)
                .min(Integer::compareTo);

        if (oFrom.isEmpty()) {
            return new ArrayList<>();
        }

        for (int i = oFrom.get(); i <= currentYear; i++) {
            if (i <= 2013){
                continue;
            }
            RatingView ratingView = new RatingView();
            ratingView.setYear(i);

            result.add(ratingView);

            final int year = i;

            Optional<Rating> oRating = ratings.stream()
                    .filter(x -> Rating.ICV_RATING_CODE.equals(x.getRatingCode()))
                    .filter(x -> year == x.getYear())
                    .findFirst();

            if (oRating.isPresent()) {
                Rating rating = oRating.get();
                RatingStatus ratingStatus = rating.getRatingStatus();
                if (RatingStatus.INDEXED.equals(ratingStatus)){
                    if (rating.getYear() <= 2013) {
                        ratingView.setPreviousMethodPoint(rating.getValueNum());
                    } else {
                        ratingView.setCurrentMethodPoint(rating.getValueNum());
                    }
                }
                ratingView.setRatingStatus(ratingStatus);
            } else {
                ratingView.setRatingStatus(RatingStatus.NOT_EVALUATED);
            }

        }

        result.sort((a, b) -> b.getYear().compareTo(a.getYear()));

        return result;
    }

    @Named("currentIcvYear")
    Integer currentIcvYear(Set<Rating> ratings, @Context Integer surveyYear) {
        return selectedYear(surveyYear);
    }

    @Named("currentReasonNotIndexing")
    ReasonNotIndexing currentReasonNotIndexing(Set<Rating> ratings, @Context Integer surveyYear) {
        if (ratings == null) return null;

        Integer maxYear = selectedYear(surveyYear);

        Optional<Rating> oRating = ratings.stream()
                .filter(x -> Rating.ICV_RATING_CODE.equals(x.getRatingCode()))
                .filter(x -> maxYear.equals(x.getYear()))
                .findFirst();

        return oRating.map(Rating::getReasonNotIndexing).orElse(null);
    }

    @Named("currentReasonNotIndexingFlag")
    Boolean currentReasonNotIndexingFlag(Set<Rating> ratings, @Context Integer surveyYear) {
        if (ratings == null) return null;

        Integer maxYear = selectedYear(surveyYear);

        Optional<Rating> oRating = ratings.stream()
                .filter(x -> Rating.ICV_RATING_CODE.equals(x.getRatingCode()))
                .filter(x -> maxYear.equals(x.getYear()))
                .findFirst();
        return oRating.map(Rating::getReasonNotIndexing).map(ReasonNotIndexing::isQuestionarieVisible).orElse(null);
    }

    @Named("currentIcvPoints")
    Double currentIcvPoints(Set<Rating> ratings, @Context Integer surveyYear) {
        if (ratings == null) return null;

        Integer maxYear = selectedYear(surveyYear);

        Optional<Rating> oRating = ratings.stream()
                .filter(x -> Rating.ICV_RATING_CODE.equals(x.getRatingCode()))
                .filter(x -> maxYear.equals(x.getYear()))
                .findFirst();

        return oRating.map(Rating::getValueNum).orElse(null);
    }
    @Named("currentStatus")
    RatingStatus currentStatus(Set<Rating> ratings, @Context Integer surveyYear) {
        if (ratings == null) return RatingStatus.NOT_EVALUATED;

        Integer maxYear = selectedYear(surveyYear);

        Optional<Rating> oRating = ratings.stream()
                .filter(x -> Rating.ICV_RATING_CODE.equals(x.getRatingCode()))
                .filter(x -> maxYear.equals(x.getYear()))
                .findFirst();

        if (oRating.isEmpty()){
            return RatingStatus.NOT_EVALUATED;
        }
        return oRating.map(Rating::getRatingStatus).orElse(null);
    }

    @Named("mnisw")
    List<RatingView> mnisw(Set<Rating> ratings) {
        if (ratings == null) return new ArrayList<>();

        Integer maxYear = configurationService.getCurrentRatingYear();

        List<RatingView> result = new ArrayList<>();

        Optional<Integer> oFrom = ratings.stream()
                .filter(x -> Rating.MNW_RATING_CODE.equals(x.getRatingCode()))
                .map(Rating::getYear)
                .min(Integer::compareTo);

        if (oFrom.isEmpty()) {
            return new ArrayList<>();
        }

        for (int i = oFrom.get(); i <= maxYear; i++) {
            RatingView ratingView = new RatingView();
            ratingView.setYear(i);

            result.add(ratingView);

            final int year = i;

            Optional<Rating> oRating = ratings.stream()
                    .filter(x -> Rating.MNW_RATING_CODE.equals(x.getRatingCode()))
                    .filter(x -> year == x.getYear())
                    .findFirst();

            if (oRating.isPresent()) {
                Rating rating = oRating.get();
                ratingView.setPreviousMethodPoint(rating.getValueNum());
                ratingView.setRatingKey(rating.getValueStr());
            }

        }
        return result;
    }

    private Integer selectedYear(Integer surveyYear) {
        return surveyYear == null ? configurationService.getCurrentRatingYear() : surveyYear;
    }
}
