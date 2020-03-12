package com.logicgate.farm.repository;

import com.logicgate.farm.domain.Barn;

import com.logicgate.farm.domain.Color;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BarnRepository extends JpaRepository<Barn, Long> {

  // additional methods can be defined here
  List<Barn> findAllByColor(Color favoriteColor);
}
