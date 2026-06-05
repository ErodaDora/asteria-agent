package com.dora.jagent.service;

import com.dora.jagent.service.impl.support.HupuLolPostSnapshot;
import com.dora.jagent.service.impl.support.LolEsportsMatchSnapshot;

import java.util.Optional;

public interface HupuLolPostService {

    Optional<HupuLolPostSnapshot> findMatchPost(LolEsportsMatchSnapshot matchSnapshot);
}
