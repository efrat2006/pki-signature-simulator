package com.authentisign.desktop.database.daos;

import org.slf4j.*;

import java.util.*;

public interface CrudDao<T, ID> {

    Logger logger = LoggerFactory.getLogger(CrudDao.class);

    void save(T entity);
    void update(T entity);
    Optional<T> findById(ID id);
    List<T> findAll();
}
