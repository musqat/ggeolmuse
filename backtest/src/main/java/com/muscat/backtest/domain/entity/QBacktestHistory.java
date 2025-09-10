package com.muscat.backtest.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;

import java.time.LocalDateTime;


@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QBacktestHistory extends EntityPathBase<BacktestHistory> {

    private static final long serialVersionUID = 1436724521L;

    public static final QBacktestHistory backtestHistory = new QBacktestHistory("backtestHistory");

    public final StringPath backtestId = createString("backtestId");

    public final EnumPath<BacktestHistory.BacktestType> backtestType = createEnum("backtestType", BacktestHistory.BacktestType.class);

    public final DateTimePath<LocalDateTime> createdAt = createDateTime("createdAt", LocalDateTime.class);

    public final StringPath requestParams = createString("requestParams");

    public final StringPath userId = createString("userId");

    public QBacktestHistory(String variable) {
        super(BacktestHistory.class, forVariable(variable));
    }

    public QBacktestHistory(Path<? extends BacktestHistory> path) {
        super(path.getType(), path.getMetadata());
    }

    public QBacktestHistory(PathMetadata metadata) {
        super(BacktestHistory.class, metadata);
    }

}