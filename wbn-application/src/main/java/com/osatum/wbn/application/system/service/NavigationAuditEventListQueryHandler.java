package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.system.dto.NavigationAuditEventListDto;
import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.shared.list.PageQueryHandler;

/**
 * Kontrakt odczytu stronicowanej listy zdarzeń nawigacji, realizowany przez warstwę dostępu do danych.
 * Korzysta ze wspólnego mechanizmu wykonania zapytania i zwracania metadanych paginacji.
 */
public interface NavigationAuditEventListQueryHandler extends PageQueryHandler<NavigationAuditEventListQuery, NavigationAuditEventListDto> {
}
