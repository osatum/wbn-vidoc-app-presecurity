package com.osatum.wbn.application.auth.dto;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutorizeIpRequest {
    private Long id;
    private String name;
}
