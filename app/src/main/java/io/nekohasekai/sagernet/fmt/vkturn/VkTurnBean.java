/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  dyhkwong                                               *
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.      *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.vkturn;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class VkTurnBean extends AbstractBean {

    public static final int MODE_WIREGUARD = 0;
    public static final int MODE_VLESS = 1;

    public static final int PROVIDER_VK = 0;
    public static final int PROVIDER_YANDEX = 1;
    public static final int PROVIDER_WB = 2;

    public Integer mode;
    public Integer provider;
    public String callLink;
    public String turnHost;
    public Integer turnPort;
    public Integer streams;
    public Boolean udp;
    public Boolean debug;
    public Boolean manualCaptcha;
    public String dns;
    public Integer allocsPerStream;
    public Boolean noDtls;
    public String sessionId;
    public Boolean protocolV1;

    public String localAddress;
    public String privateKey;
    public String peerPublicKey;
    public String peerPreSharedKey;
    public Integer mtu;
    public String reserved;
    public Integer keepaliveInterval;
    public String uuid;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (serverPort == null || serverPort == 1080) serverPort = 56000;
        if (mode == null) mode = MODE_WIREGUARD;
        if (provider == null) provider = PROVIDER_VK;
        if (callLink == null) callLink = "";
        if (turnHost == null) turnHost = "turn.vk.com";
        if (turnPort == null || turnPort <= 0) turnPort = 443;
        if (streams == null || streams <= 0) streams = 1;
        if (udp == null) udp = true;
        if (debug == null) debug = false;
        if (manualCaptcha == null) manualCaptcha = false;
        if (dns == null) dns = "";
        if (allocsPerStream == null) allocsPerStream = 0;
        if (noDtls == null) noDtls = false;
        if (sessionId == null) sessionId = "";
        if (protocolV1 == null) protocolV1 = false;
        if (localAddress == null) localAddress = "";
        if (privateKey == null) privateKey = "";
        if (peerPublicKey == null) peerPublicKey = "";
        if (peerPreSharedKey == null) peerPreSharedKey = "";
        if (mtu == null || mtu <= 0) mtu = 1280;
        if (reserved == null) reserved = "";
        if (keepaliveInterval == null) keepaliveInterval = 0;
        if (uuid == null) uuid = "";
    }

    @Override
    public String network() {
        return mode == MODE_VLESS ? "tcp" : "udp";
    }

    @Override
    public boolean isInsecure() {
        return noDtls;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeInt(mode);
        output.writeInt(provider);
        output.writeString(callLink);
        output.writeString(turnHost);
        output.writeInt(turnPort);
        output.writeInt(streams);
        output.writeBoolean(udp);
        output.writeBoolean(debug);
        output.writeBoolean(manualCaptcha);
        output.writeString(dns);
        output.writeInt(allocsPerStream);
        output.writeBoolean(noDtls);
        output.writeString(sessionId);
        output.writeBoolean(protocolV1);
        output.writeString(localAddress);
        output.writeString(privateKey);
        output.writeString(peerPublicKey);
        output.writeString(peerPreSharedKey);
        output.writeInt(mtu);
        output.writeString(reserved);
        output.writeInt(keepaliveInterval);
        output.writeString(uuid);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        input.readInt();
        super.deserialize(input);
        mode = input.readInt();
        provider = input.readInt();
        callLink = input.readString();
        turnHost = input.readString();
        turnPort = input.readInt();
        streams = input.readInt();
        udp = input.readBoolean();
        debug = input.readBoolean();
        manualCaptcha = input.readBoolean();
        dns = input.readString();
        allocsPerStream = input.readInt();
        noDtls = input.readBoolean();
        sessionId = input.readString();
        protocolV1 = input.readBoolean();
        localAddress = input.readString();
        privateKey = input.readString();
        peerPublicKey = input.readString();
        peerPreSharedKey = input.readString();
        mtu = input.readInt();
        reserved = input.readString();
        keepaliveInterval = input.readInt();
        uuid = input.readString();
    }

    @NotNull
    @Override
    public VkTurnBean clone() {
        return KryoConverters.deserialize(new VkTurnBean(), KryoConverters.serialize(this));
    }

    public static final Creator<VkTurnBean> CREATOR = new CREATOR<>() {
        @NonNull
        @Override
        public VkTurnBean newInstance() {
            return new VkTurnBean();
        }

        @Override
        public VkTurnBean[] newArray(int size) {
            return new VkTurnBean[size];
        }
    };
}
