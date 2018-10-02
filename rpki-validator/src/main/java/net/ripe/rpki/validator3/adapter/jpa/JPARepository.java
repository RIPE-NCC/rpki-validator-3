/**
 * The BSD License
 *
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the RIPE NCC nor the names of its contributors may be
 *     used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.adapter.jpa;

import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.types.EntityPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import net.ripe.rpki.validator3.api.Paging;
import net.ripe.rpki.validator3.api.util.Dates;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.sql.Timestamp;
import java.util.Date;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

abstract class JPARepository<T> {
    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected JPAQueryFactory queryFactory;

    private final EntityPath<T> entityPath;

    protected JPARepository(EntityPath<T> entityPath) {
        this.entityPath = entityPath;
    }

    public void add(T entity) {
        entityManager.persist(entity);
    }

    public void remove(T entity) {
        entityManager.remove(entity);
    }

    public T get(long id) {
        return get(entityPath.getType(), id);
    }

    public <U extends T> U get(Class<U> type, long id) {
        U result = entityManager.getReference(type, id);
        Hibernate.initialize(result);
        return result;
    }

    protected <T> Stream<T> stream(JPAQuery<T> query) {
        CloseableIterator<T> results = query.iterate();
        Iterable<T> iterable = () -> results;
        return StreamSupport.stream(iterable.spliterator(), false).onClose(results::close);
    }

    protected JPAQuery<T> select() {
        return queryFactory.selectFrom(entityPath);
    }

    public Stream<T> all() {
        return stream(select());
    }

    public long clear() {
        return queryFactory.delete(entityPath).execute();
    }

    protected Query sql(String sql) {
        return entityManager.createNativeQuery(sql);
    }

    protected String asString(Object o) {
        return o == null ? null : o.toString();
    }

    protected int asInt(Object o) {
        return o == null ? null : Integer.parseInt(o.toString());
    }

    protected Date asDate(Object d) {
        return d == null ? null : new Date(((Timestamp) d).getTime());
    }

    protected Boolean asBoolean(Object o) {
        return o == null ? null : (Boolean)o;
    }

    protected <T> JPAQuery<T> applyPaging(JPAQuery<T> query, Paging paging) {
        if (paging != null) {
            final Long startFrom = paging.getStartFrom();
            if (startFrom != null) {
                query.offset(startFrom);
            }
            final Long pageSize = paging.getPageSize();
            if (pageSize != null) {
                query.limit(pageSize);
            }
        }
        return query;
    }
}
