package com.muscat.marketdata.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;

import java.math.BigDecimal;
import java.time.LocalDate;


/**
 * QFxRate is a Querydsl query type for FxRate
 */
@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QFxRate extends EntityPathBase<FxRate> {

    private static final long serialVersionUID = 1256859829L;

    public static final QFxRate fxRate = new QFxRate("fxRate");

    public final DatePath<LocalDate> date = createDate("date", LocalDate.class);

    public final NumberPath<BigDecimal> rate = createNumber("rate", BigDecimal.class);

    public QFxRate(String variable) {
        super(FxRate.class, forVariable(variable));
    }

    public QFxRate(Path<? extends FxRate> path) {
        super(path.getType(), path.getMetadata());
    }

    public QFxRate(PathMetadata metadata) {
        super(FxRate.class, metadata);
    }

}