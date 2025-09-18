package com.muscat.marketdata.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;

import java.math.BigDecimal;
import java.time.LocalDate;


@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QCandle extends EntityPathBase<Candle> {

    private static final long serialVersionUID = -1503618563L;

    public static final QCandle candle = new QCandle("candle");

    public final NumberPath<BigDecimal> adjustedClose = createNumber("adjustedClose", BigDecimal.class);

    public final NumberPath<BigDecimal> close = createNumber("close", BigDecimal.class);

    public final StringPath currency = createString("currency");

    public final DatePath<LocalDate> date = createDate("date", LocalDate.class);

    public final NumberPath<BigDecimal> dividendAmount = createNumber("dividendAmount", BigDecimal.class);

    public final NumberPath<BigDecimal> high = createNumber("high", BigDecimal.class);

    public final NumberPath<Long> id = createNumber("id", Long.class);

    public final NumberPath<BigDecimal> low = createNumber("low", BigDecimal.class);

    public final NumberPath<BigDecimal> open = createNumber("open", BigDecimal.class);

    public final NumberPath<BigDecimal> splitCoefficient = createNumber("splitCoefficient", BigDecimal.class);

    public final StringPath symbol = createString("symbol");

    public final NumberPath<Long> volume = createNumber("volume", Long.class);

    public QCandle(String variable) {
        super(Candle.class, forVariable(variable));
    }

    public QCandle(Path<? extends Candle> path) {
        super(path.getType(), path.getMetadata());
    }

    public QCandle(PathMetadata metadata) {
        super(Candle.class, metadata);
    }

}