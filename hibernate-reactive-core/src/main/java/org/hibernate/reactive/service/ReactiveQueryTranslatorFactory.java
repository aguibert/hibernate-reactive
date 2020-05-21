package org.hibernate.reactive.service;

import java.util.Map;

import org.hibernate.engine.query.spi.EntityGraphQueryHint;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.hql.spi.FilterTranslator;
import org.hibernate.hql.spi.QueryTranslator;
import org.hibernate.hql.spi.QueryTranslatorFactory;
import org.hibernate.reactive.impl.ReactiveQueryTranslatorImpl;

public class ReactiveQueryTranslatorFactory implements QueryTranslatorFactory {

	@Override
	public QueryTranslator createQueryTranslator(
			String queryIdentifier,
			String queryString,
			Map filters,
			SessionFactoryImplementor factory,
			EntityGraphQueryHint entityGraphQueryHint) {
		return new ReactiveQueryTranslatorImpl( queryIdentifier, queryString, filters, factory, entityGraphQueryHint );
	}

	@Override
	public FilterTranslator createFilterTranslator(
			String queryIdentifier, String queryString, Map filters, SessionFactoryImplementor factory) {
		return new ReactiveQueryTranslatorImpl( queryIdentifier, queryString, filters, factory  );
	}
}