package com.osatum.wbn.domain.model.survey.model;

import java.util.Arrays;

public enum SocialMedia {

    FACEBOOK(1L), INSTAGRAM(2L), LINKEDIN(3L), X(4L), YOUTUBE(5L);
    private static final long serialVersionUID = 1L;
    private Long id;

    SocialMedia(Long id) {
        this.id = id;
    }

    public static SocialMedia fromValue(Long id){
        return Arrays.stream(SocialMedia.values()).sequential().filter(s -> s.id.equals(id)).findFirst().orElse(null);
    }

}
