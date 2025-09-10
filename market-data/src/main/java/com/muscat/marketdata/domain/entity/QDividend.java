package com.muscat.marketdata.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * QDividend is a Querydsl query type for Dividend
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QDividend extends EntityPathBase<Dividend> {

    private static final long serialVersionUID = -1884498467L;

    public static final QDividend dividend = new QDividend("dividend");

    public final NumberPath<BigDecimal> amount = createNumber("amount", BigDecimal.class);

    public final StringPath currency = createString("currency");

    public final DatePath<LocalDate> exDate = createDate("exDate", LocalDate.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final StringPath symbol = createString("symbol");

    public QDividend(String variable) {
        super(Dividend.class, forVariable(variable));
    }

    public QDividend(Path<? extends Dividend> path) {
        super(path.getType(), path.getMetadata());
    }

    public QDividend(PathMetadata metadata) {
        super(Dividend.class, metadata);
    }

}