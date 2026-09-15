/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  dyhkwong                                               *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 * (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.      *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.byebyedpi;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class ByeByeDPIBean extends AbstractBean {

    public static final int DESYNC_NONE = 0;
    public static final int DESYNC_SPLIT = 1;
    public static final int DESYNC_DISORDER = 2;
    public static final int DESYNC_FAKE = 3;
    public static final int DESYNC_OOB = 4;
    public static final int DESYNC_DISOOB = 5;

    public static final int HOSTS_DISABLE = 0;
    public static final int HOSTS_BLACKLIST = 1;
    public static final int HOSTS_WHITELIST = 2;

    public static final String DEFAULT_COMMAND_LINE = "-o1 -a1 -r-5+se";

    public Boolean useCommandLine;
    public String commandLine;

    public Integer maxConnections;
    public Integer bufferSize;
    public Integer defaultTtl;
    public Boolean noDomain;
    public Boolean desyncHttp;
    public Boolean desyncHttps;
    public Boolean desyncUdp;
    public Integer desyncMethod;
    public Integer splitPosition;
    public Boolean splitAtHost;
    public Integer fakeTtl;
    public String fakeSni;
    public String oobChar;
    public Boolean hostMixedCase;
    public Boolean domainMixedCase;
    public Boolean hostRemoveSpaces;
    public Boolean tlsRecordSplit;
    public Integer tlsRecordSplitPosition;
    public Boolean tlsRecordSplitAtSni;
    public Integer hostsMode;
    public String hosts;
    public Boolean tcpFastOpen;
    public Integer udpFakeCount;
    public Boolean dropSack;
    public Integer fakeOffset;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (useCommandLine == null) useCommandLine = true;
        if (commandLine == null) commandLine = DEFAULT_COMMAND_LINE;
        if (maxConnections == null || maxConnections <= 0) maxConnections = 512;
        if (bufferSize == null || bufferSize <= 0) bufferSize = 16384;
        if (defaultTtl == null || defaultTtl < 0) defaultTtl = 0;
        if (noDomain == null) noDomain = false;
        if (desyncHttp == null) desyncHttp = true;
        if (desyncHttps == null) desyncHttps = true;
        if (desyncUdp == null) desyncUdp = true;
        if (desyncMethod == null) desyncMethod = DESYNC_OOB;
        if (splitPosition == null) splitPosition = 1;
        if (splitAtHost == null) splitAtHost = false;
        if (fakeTtl == null || fakeTtl < 0) fakeTtl = 8;
        if (fakeSni == null) fakeSni = "www.iana.org";
        if (oobChar == null || oobChar.isEmpty()) oobChar = "a";
        if (hostMixedCase == null) hostMixedCase = false;
        if (domainMixedCase == null) domainMixedCase = false;
        if (hostRemoveSpaces == null) hostRemoveSpaces = false;
        if (tlsRecordSplit == null) tlsRecordSplit = false;
        if (tlsRecordSplitPosition == null) tlsRecordSplitPosition = 0;
        if (tlsRecordSplitAtSni == null) tlsRecordSplitAtSni = false;
        if (hostsMode == null) hostsMode = HOSTS_DISABLE;
        if (hosts == null) hosts = "";
        if (tcpFastOpen == null) tcpFastOpen = false;
        if (udpFakeCount == null || udpFakeCount < 0) udpFakeCount = 1;
        if (dropSack == null) dropSack = false;
        if (fakeOffset == null || fakeOffset < 0) fakeOffset = 0;
    }

    @Override
    public String network() {
        return "tcp";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeBoolean(useCommandLine);
        output.writeString(commandLine);
        output.writeInt(maxConnections);
        output.writeInt(bufferSize);
        output.writeInt(defaultTtl);
        output.writeBoolean(noDomain);
        output.writeBoolean(desyncHttp);
        output.writeBoolean(desyncHttps);
        output.writeBoolean(desyncUdp);
        output.writeInt(desyncMethod);
        output.writeInt(splitPosition);
        output.writeBoolean(splitAtHost);
        output.writeInt(fakeTtl);
        output.writeString(fakeSni);
        output.writeString(oobChar);
        output.writeBoolean(hostMixedCase);
        output.writeBoolean(domainMixedCase);
        output.writeBoolean(hostRemoveSpaces);
        output.writeBoolean(tlsRecordSplit);
        output.writeInt(tlsRecordSplitPosition);
        output.writeBoolean(tlsRecordSplitAtSni);
        output.writeInt(hostsMode);
        output.writeString(hosts);
        output.writeBoolean(tcpFastOpen);
        output.writeInt(udpFakeCount);
        output.writeBoolean(dropSack);
        output.writeInt(fakeOffset);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        input.readInt();
        super.deserialize(input);
        useCommandLine = input.readBoolean();
        commandLine = input.readString();
        maxConnections = input.readInt();
        bufferSize = input.readInt();
        defaultTtl = input.readInt();
        noDomain = input.readBoolean();
        desyncHttp = input.readBoolean();
        desyncHttps = input.readBoolean();
        desyncUdp = input.readBoolean();
        desyncMethod = input.readInt();
        splitPosition = input.readInt();
        splitAtHost = input.readBoolean();
        fakeTtl = input.readInt();
        fakeSni = input.readString();
        oobChar = input.readString();
        hostMixedCase = input.readBoolean();
        domainMixedCase = input.readBoolean();
        hostRemoveSpaces = input.readBoolean();
        tlsRecordSplit = input.readBoolean();
        tlsRecordSplitPosition = input.readInt();
        tlsRecordSplitAtSni = input.readBoolean();
        hostsMode = input.readInt();
        hosts = input.readString();
        tcpFastOpen = input.readBoolean();
        udpFakeCount = input.readInt();
        dropSack = input.readBoolean();
        fakeOffset = input.readInt();
    }

    @NotNull
    @Override
    public ByeByeDPIBean clone() {
        return KryoConverters.deserialize(new ByeByeDPIBean(), KryoConverters.serialize(this));
    }

    public static final Creator<ByeByeDPIBean> CREATOR = new CREATOR<>() {
        @NonNull
        @Override
        public ByeByeDPIBean newInstance() {
            return new ByeByeDPIBean();
        }

        @Override
        public ByeByeDPIBean[] newArray(int size) {
            return new ByeByeDPIBean[size];
        }
    };
}
