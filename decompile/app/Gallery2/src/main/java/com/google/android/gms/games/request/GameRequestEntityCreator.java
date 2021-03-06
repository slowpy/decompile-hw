package com.google.android.gms.games.request;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable.Creator;
import com.google.android.gms.common.internal.safeparcel.a;
import com.google.android.gms.common.internal.safeparcel.b;
import com.google.android.gms.games.GameEntity;
import com.google.android.gms.games.PlayerEntity;
import java.util.ArrayList;

/* compiled from: Unknown */
public class GameRequestEntityCreator implements Creator<GameRequestEntity> {
    static void a(GameRequestEntity gameRequestEntity, Parcel parcel, int i) {
        int p = b.p(parcel);
        b.a(parcel, 1, gameRequestEntity.getGame(), i, false);
        b.c(parcel, 1000, gameRequestEntity.getVersionCode());
        b.a(parcel, 2, gameRequestEntity.getSender(), i, false);
        b.a(parcel, 3, gameRequestEntity.getData(), false);
        b.a(parcel, 4, gameRequestEntity.getRequestId(), false);
        b.b(parcel, 5, gameRequestEntity.fU(), false);
        b.c(parcel, 7, gameRequestEntity.getType());
        b.a(parcel, 9, gameRequestEntity.getCreationTimestamp());
        b.a(parcel, 10, gameRequestEntity.getExpirationTimestamp());
        b.a(parcel, 11, gameRequestEntity.gf(), false);
        b.D(parcel, p);
    }

    public GameRequestEntity createFromParcel(Parcel parcel) {
        int o = a.o(parcel);
        int i = 0;
        GameEntity gameEntity = null;
        PlayerEntity playerEntity = null;
        byte[] bArr = null;
        String str = null;
        ArrayList arrayList = null;
        int i2 = 0;
        long j = 0;
        long j2 = 0;
        Bundle bundle = null;
        while (parcel.dataPosition() < o) {
            int n = a.n(parcel);
            switch (a.S(n)) {
                case 1:
                    gameEntity = (GameEntity) a.a(parcel, n, GameEntity.CREATOR);
                    break;
                case 2:
                    playerEntity = (PlayerEntity) a.a(parcel, n, PlayerEntity.CREATOR);
                    break;
                case 3:
                    bArr = a.p(parcel, n);
                    break;
                case 4:
                    str = a.m(parcel, n);
                    break;
                case 5:
                    arrayList = a.c(parcel, n, PlayerEntity.CREATOR);
                    break;
                case 7:
                    i2 = a.g(parcel, n);
                    break;
                case 9:
                    j = a.h(parcel, n);
                    break;
                case 10:
                    j2 = a.h(parcel, n);
                    break;
                case 11:
                    bundle = a.o(parcel, n);
                    break;
                case 1000:
                    i = a.g(parcel, n);
                    break;
                default:
                    a.b(parcel, n);
                    break;
            }
        }
        if (parcel.dataPosition() == o) {
            return new GameRequestEntity(i, gameEntity, playerEntity, bArr, str, arrayList, i2, j, j2, bundle);
        }
        throw new a.a("Overread allowed size end=" + o, parcel);
    }

    public GameRequestEntity[] newArray(int size) {
        return new GameRequestEntity[size];
    }
}
