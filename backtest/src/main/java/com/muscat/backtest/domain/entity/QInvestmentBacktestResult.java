package com.muscat.backtest.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;

import java.time.LocalDateTime;


@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QInvestmentBacktestResult extends EntityPathBase<InvestmentBacktestResult> {

    private static final long serialVersionUID = 1123681849L;

    public static final QInvestmentBacktestResult investmentBacktestResult = new QInvestmentBacktestResult("investmentBacktestResult");

    public final StringPath backtestResult = createString("backtestResult");

    public final DateTimePath<LocalDateTime> calculatedAt = createDateTime("calculatedAt", LocalDateTime.class);

    public final DateTimePath<LocalDateTime> createdAt = createDateTime("createdAt", LocalDateTime.class);

    public final NumberPath<Long> executionTimeMs = createNumber("executionTimeMs", Long.class);

    public final DateTimePath<LocalDateTime> nextScheduledAt = createDateTime("nextScheduledAt", LocalDateTime.class);

    public final StringPath resultId = createString("resultId");

    public final EnumPath<InvestmentBacktestResult.CalculationStatus> status = createEnum("status", InvestmentBacktestResult.CalculationStatus.class);

    public final DateTimePath<LocalDateTime> updatedAt = createDateTime("updatedAt", LocalDateTime.class);

    public final StringPath userId = createString("userId");

    public QInvestmentBacktestResult(String variable) {
        super(InvestmentBacktestResult.class, forVariable(variable));
    }

    public QInvestmentBacktestResult(Path<? extends InvestmentBacktestResult> path) {
        super(path.getType(), path.getMetadata());
    }

    public QInvestmentBacktestResult(PathMetadata metadata) {
        super(InvestmentBacktestResult.class, metadata);
    }

}