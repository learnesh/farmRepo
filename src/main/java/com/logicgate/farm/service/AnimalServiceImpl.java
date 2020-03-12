package com.logicgate.farm.service;

import com.logicgate.farm.domain.Animal;
import com.logicgate.farm.domain.Barn;
import com.logicgate.farm.domain.Color;
import com.logicgate.farm.repository.AnimalRepository;
import com.logicgate.farm.repository.BarnRepository;

import com.logicgate.farm.util.FarmUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Array;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Service
@Transactional
public class AnimalServiceImpl implements AnimalService {

  private final AnimalRepository animalRepository;

  private final BarnRepository barnRepository;

  @Autowired
  public AnimalServiceImpl(AnimalRepository animalRepository, BarnRepository barnRepository) {
    this.animalRepository = animalRepository;
    this.barnRepository = barnRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Animal> findAll() {
    return animalRepository.findAll();
  }

  @Override
  public void deleteAll() {
    animalRepository.deleteAll();
  }

  @Override
  public Animal addToFarm(Animal animal) {
    // TODO: implementation of this method
    addToBarn(animal);
//    reorganizeBarns(animal);
    return animalRepository.save(animal);
  }

  @Override
  public void addToFarm(List<Animal> animals) {
    animals.forEach(this::addToFarm);
  }

  @Override
  public void removeFromFarm(Animal animal) {
    // TODO: implementation of this method

    // remove animal from the animal repository
    animalRepository.delete(animal);

    // make things right
    // 1. equidistribute among barns

    // 2. go look for the "up to one" (either 0 or 1) empty barn, and if there is an
    // empty barn, simply delete the barn

//    reDistributeOneAnimalByBarnColor(animal.getFavoriteColor());
    // delete the empty barn, if there is an empty barn
    // delete the barn
    barnRepository.findAll()
      .stream()
      .filter(barn -> getCountAnimalInBarn(barn) == 0)
      .findFirst()
      .ifPresent(barnRepository::delete);

    optimizeNumberOfBarns(animal);
    reDistributeOneAnimalByBarnColor(animal.getFavoriteColor());

//    removeEmptyBarn();
//    reorganizeBarns(animal);
  }

  @Override
  public void removeFromFarm(List<Animal> animals) {
    animals.forEach(animal -> removeFromFarm(animalRepository.getOne(animal.getId())));
  }

  public void optimizeNumberOfBarns(Animal animal) {
    List<Animal> animals = animalRepository.findByFavoriteColor(animal.getFavoriteColor());
    List<Barn> barns = barnRepository.findAllByColor(animal.getFavoriteColor());

    int barnSize = barns.size();

    while(barnSize > (animals.size()/20) + 1) {
      animals.stream().filter(animal1 -> animal1.getBarn() == barns.get(barns.size() - 1)).forEach(animal1 -> animal1.setBarn(null));
      animals.stream().filter(animal1 -> animal1.getBarn() == null).forEach(animal1 -> animalRepository.save(animal1.setBarn(barns.get(0))));

      barnRepository.delete(barns.get(barns.size() - 1));

      reDistributeOneAnimalByBarnColor(animal.getFavoriteColor());

      barnSize = barnRepository.findAllByColor(animal.getFavoriteColor()).size();
    }

    if(barnSize == animals.size()/20) {
      barnRepository.save(new Barn(UUID.randomUUID().toString(), animal.getFavoriteColor()));
      reDistributeOneAnimalByBarnColor(animal.getFavoriteColor());
    }

  }

  private int getCountAnimalInBarn(Barn barn) {
    return animalRepository.countByBarn(barn);
  }

  private void setAndSaveBarn(Animal animal, Barn barn) {
    Animal animalWithBarnSet = animal.setBarn(barn);
    animalRepository.save(animalWithBarnSet);
    reDistributeOneAnimalByBarnColor(animal.getFavoriteColor());
  }

  private void reDistributeOneAnimalByBarnColor(Color color) {
    List<Barn> barnsSorted = barnRepository.findAllByColor(color)
      .stream()
      .sorted(Comparator.comparingInt(this::getCountAnimalInBarn))
      .collect(Collectors.toList());

    Barn minBarn = barnsSorted.get(0);
    Barn maxBarn = barnsSorted.get(barnsSorted.size() - 1);

    if (getCountAnimalInBarn(maxBarn) - getCountAnimalInBarn(minBarn) > 1) { // then we need to move from max barn to min barn
      Animal firstInMaxBarn = animalRepository.findFirstByBarn(maxBarn);
      setAndSaveBarn(firstInMaxBarn, minBarn);
    }
  }

  private void createBarnForAnimal(Animal animal) {
    Barn newBarn = barnRepository.save(new Barn(UUID.randomUUID().toString(), animal.getFavoriteColor()));
    setAndSaveBarn(animal, newBarn);
  }

  public void addToBarn(Animal animal) {
    List<Barn> barnsForAnimalColor = barnRepository.findAllByColor(animal.getFavoriteColor()); // todo find by capacity less than

    if (barnsForAnimalColor.isEmpty()) {
      createBarnForAnimal(animal);
    } else { // there is already a barn for this color
      // if there are any non-full barns for this color already
      List<Barn> matchingBarns = barnsForAnimalColor.stream()
        .filter(barn -> getCountAnimalInBarn(barn) < FarmUtils.barnCapacity())
        .sorted(Comparator.comparingInt(this::getCountAnimalInBarn))
        .collect(Collectors.toList());

      if (matchingBarns.isEmpty()) { /* no barns currently exist for this animal to inhabit, so add a barn for the color, add the animal to
        that barn */
        createBarnForAnimal(animal);
      } else { // if there's a candidate barn, add the animal to that barn
        setAndSaveBarn(animal, matchingBarns.get(0));
      }
    }
  }
}
