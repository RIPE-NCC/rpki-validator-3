package net.ripe.rpki.validator3.adapter.jpa;

import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.types.EntityPath;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
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
}
