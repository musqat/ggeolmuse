package com.muscat.trade.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QHoldings extends EntityPathBase<Holdings> {

    private static final long serialVersionUID = 347131761L;

    public static final QHoldings holdings = new QHoldings("holdings");

    public final StringPath accountId = createString("accountId");

    public final NumberPath<BigDecimal> avgPurchasePrice = createNumber("avgPurchasePrice", BigDecimal.class);

    public final DateTimePath<LocalDateTime> createdAt = createDateTime("createdAt", LocalDateTime.class);

    public final StringPath holdingId = createString("holdingId");

    public final DateTimePath<LocalDateTime> lastUpdatedAt = createDateTime("lastUpdatedAt", LocalDateTime.class);

    public final StringPath symbol = createString("symbol");

    public final NumberPath<BigDecimal> totalInvestedAmount = createNumber("totalInvestedAmount", BigDecimal.class);

    public final NumberPath<BigDecimal> totalQuantity = createNumber("totalQuantity", BigDecimal.class);

    public final StringPath userId = createString("userId");

    public QHoldings(String variable) {
        super(Holdings.class, forVariable(variable));
    }

    public QHoldings(Path<? extends Holdings> path) {
        super(path.getType(), path.getMetadata());
    }

    public QHoldings(PathMetadata metadata) {
        super(Holdings.class, metadata);
    }

}
