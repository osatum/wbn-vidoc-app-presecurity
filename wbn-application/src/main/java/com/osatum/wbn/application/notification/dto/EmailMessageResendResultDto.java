package com.osatum.wbn.application.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessageResendResultDto {
    private EmailMessageResendStatus status;
    private EmailMessageLogListDto queuedMessage;
}
