package com.dora.jagent.service;

import com.dora.jagent.service.impl.support.LolEsportsMatchSnapshot;

import java.util.List;

public interface LolEsportsService {

    List<LolEsportsMatchSnapshot> getTodayKeyMatches();
}
