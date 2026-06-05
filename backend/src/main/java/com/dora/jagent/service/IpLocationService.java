package com.dora.jagent.service;

import com.dora.jagent.service.impl.support.IpLocationSnapshot;

public interface IpLocationService {

    IpLocationSnapshot locateByIp(String ip);
}
