/******************************************************************************
 * Copyright (C) 2021 by nekohasekai <contact-git@sekai.icu>                  *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.wireguard;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class WireGuardBean extends AbstractBean {

    public String localAddress;
    public String privateKey;
    public String peerPublicKey;
    public String peerPreSharedKey;
    public Integer mtu;
    public String reserved;
    public Integer keepaliveInterval;
    public Integer amneziaJc;
    public Integer amneziaJmin;
    public Integer amneziaJmax;
    public Integer amneziaS1;
    public Integer amneziaS2;
    public Integer amneziaS3;
    public Integer amneziaS4;
    public String amneziaH1;
    public String amneziaH2;
    public String amneziaH3;
    public String amneziaH4;
    public String amneziaI1;
    public String amneziaI2;
    public String amneziaI3;
    public String amneziaI4;
    public String amneziaI5;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (localAddress == null) localAddress = "";
        if (privateKey == null) privateKey = "";
        if (peerPublicKey == null) peerPublicKey = "";
        if (peerPreSharedKey == null) peerPreSharedKey = "";
        // wireguard default mtu
        if (mtu == null) mtu = 1420;
        if (reserved == null) reserved = "";
        if (keepaliveInterval == null) keepaliveInterval = 0;
        if (amneziaJc == null) amneziaJc = 0;
        if (amneziaJmin == null) amneziaJmin = 0;
        if (amneziaJmax == null) amneziaJmax = 0;
        if (amneziaS1 == null) amneziaS1 = 0;
        if (amneziaS2 == null) amneziaS2 = 0;
        if (amneziaS3 == null) amneziaS3 = 0;
        if (amneziaS4 == null) amneziaS4 = 0;
        if (amneziaH1 == null) amneziaH1 = "";
        if (amneziaH2 == null) amneziaH2 = "";
        if (amneziaH3 == null) amneziaH3 = "";
        if (amneziaH4 == null) amneziaH4 = "";
        if (amneziaI1 == null) amneziaI1 = "";
        if (amneziaI2 == null) amneziaI2 = "";
        if (amneziaI3 == null) amneziaI3 = "";
        if (amneziaI4 == null) amneziaI4 = "";
        if (amneziaI5 == null) amneziaI5 = "";
    }

    public boolean hasAmneziaOptions() {
        return (amneziaJc != null && amneziaJc > 0)
                || (amneziaJmin != null && amneziaJmin > 0)
                || (amneziaJmax != null && amneziaJmax > 0)
                || (amneziaS1 != null && amneziaS1 > 0)
                || (amneziaS2 != null && amneziaS2 > 0)
                || (amneziaS3 != null && amneziaS3 > 0)
                || (amneziaS4 != null && amneziaS4 > 0)
                || notBlank(amneziaH1)
                || notBlank(amneziaH2)
                || notBlank(amneziaH3)
                || notBlank(amneziaH4)
                || notBlank(amneziaI1)
                || notBlank(amneziaI2)
                || notBlank(amneziaI3)
                || notBlank(amneziaI4)
                || notBlank(amneziaI5);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(5);
        super.serialize(output);
        output.writeString(localAddress);
        output.writeString(privateKey);
        output.writeString(peerPublicKey);
        output.writeString(peerPreSharedKey);
        output.writeInt(mtu);
        output.writeString(reserved);
        output.writeInt(keepaliveInterval);
        output.writeInt(amneziaJc);
        output.writeInt(amneziaJmin);
        output.writeInt(amneziaJmax);
        output.writeInt(amneziaS1);
        output.writeInt(amneziaS2);
        output.writeInt(amneziaS3);
        output.writeInt(amneziaS4);
        output.writeString(amneziaH1);
        output.writeString(amneziaH2);
        output.writeString(amneziaH3);
        output.writeString(amneziaH4);
        output.writeString(amneziaI1);
        output.writeString(amneziaI2);
        output.writeString(amneziaI3);
        output.writeString(amneziaI4);
        output.writeString(amneziaI5);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        localAddress = input.readString();
        privateKey = input.readString();
        peerPublicKey = input.readString();
        peerPreSharedKey = input.readString();
        if (version <= 2) {
            // On earlier versions, the code copied from Xray accepts non-standard addresses and keys.
            // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L75
            // https://github.com/XTLS/Xray-core/blob/d8934cf83946e88210b6bb95d793bc06e12b6db8/infra/conf/wireguard.go#L126-L148
            if (localAddress.isEmpty()) {
                localAddress = "10.0.0.1/32\nfd59:7153:2388:b5fd:0000:0000:0000:0001/128";
            }
            if (!privateKey.isEmpty()) {
                privateKey = String.format("%-44s", privateKey.replace('_', '/').replace('-', '+')).replace(' ', '=');
            }
            if (!peerPublicKey.isEmpty()) {
                peerPublicKey = String.format("%-44s", peerPublicKey.replace('_', '/').replace('-', '+')).replace(' ', '=');
            }
            if (!peerPreSharedKey.isEmpty()) {
                peerPreSharedKey = String.format("%-44s", peerPreSharedKey.replace('_', '/').replace('-', '+')).replace(' ', '=');
            }
        }
        if (version >= 1) {
            mtu = input.readInt();
        }
        if (version >= 2) {
            reserved = input.readString();
        }
        if (version >= 4) {
            keepaliveInterval = input.readInt();
        }
        if (version >= 5) {
            amneziaJc = input.readInt();
            amneziaJmin = input.readInt();
            amneziaJmax = input.readInt();
            amneziaS1 = input.readInt();
            amneziaS2 = input.readInt();
            amneziaS3 = input.readInt();
            amneziaS4 = input.readInt();
            amneziaH1 = input.readString();
            amneziaH2 = input.readString();
            amneziaH3 = input.readString();
            amneziaH4 = input.readString();
            amneziaI1 = input.readString();
            amneziaI2 = input.readString();
            amneziaI3 = input.readString();
            amneziaI4 = input.readString();
            amneziaI5 = input.readString();
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof WireGuardBean bean)) return;
        bean.mtu = mtu;
    }

    @NotNull
    @Override
    public WireGuardBean clone() {
        return KryoConverters.deserialize(new WireGuardBean(), KryoConverters.serialize(this));
    }

    public static final Creator<WireGuardBean> CREATOR = new CREATOR<>() {
        @NonNull
        @Override
        public WireGuardBean newInstance() {
            return new WireGuardBean();
        }

        @Override
        public WireGuardBean[] newArray(int size) {
            return new WireGuardBean[size];
        }
    };
}
