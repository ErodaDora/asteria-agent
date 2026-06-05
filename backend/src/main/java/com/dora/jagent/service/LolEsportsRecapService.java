package com.dora.jagent.service;

import com.dora.jagent.service.impl.support.LolEsportsMatchSnapshot;

import java.util.List;
import java.util.Map;

public interface LolEsportsRecapService {

    Map<String, String> generateRecaps(List<LolEsportsMatchSnapshot> matches);
}
