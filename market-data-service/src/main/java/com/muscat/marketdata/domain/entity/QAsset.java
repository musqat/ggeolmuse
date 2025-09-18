package com.muscat.marketdata.domain.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.processing.Generated;
import com.querydsl.core.types.Path;


@Generated("com.querydsl.codegen.DefaultEntitySerializer")
public class QAsset extends EntityPathBase<Asset> {

    private static final long serialVersionUID = -2054451473L;

    public static final QAsset asset = new QAsset("asset");

    public final StringPath assetType = createString("assetType");

    public final StringPath country = createString("country");

    public final StringPath currency = createString("currency");

    public final StringPath name = createString("name");

    public final StringPath symbol = createString("symbol");

    public QAsset(String variable) {
        super(Asset.class, forVariable(variable));
    }

    public QAsset(Path<? extends Asset> path) {
        super(path.getType(), path.getMetadata());
    }

    public QAsset(PathMetadata metadata) {
        super(Asset.class, metadata);
    }

}