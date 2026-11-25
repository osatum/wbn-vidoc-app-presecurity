package com.osatum.wbn.application.shared.list;


public interface PageQueryHandler<F extends PageRequest, R> extends java.io.Serializable{
    PageResult<R> execute(F filterForm);
}