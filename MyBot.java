package PA.HaliteJava;

import java.util.ArrayList;
import java.util.List;
public class MyBot {
  public static void main(String[] args) throws java.io.IOException {
    final InitPackage iPackage = Networking.getInit();
    final int myID = iPackage.myID;
    final GameMap gameMap = iPackage.map;

    int avergProduction = 0;
    int avergStrength = 0;
    /* Productia media a hartii (+1 eroare)*/
    for (int y = 0; y < gameMap.height; ++y) {
      for (int x = 0; x < gameMap.width; ++x) {
        avergProduction += gameMap.getLocation(x, y).getSite().production;
      }
    }
    avergProduction = avergProduction / (gameMap.width * gameMap.height) + 1;
    /* Strength-ul mediu pe celula al hartii (+1 eroare)*/
    for (int y = 0; y < gameMap.height; ++y) {
      for (int x = 0; x < gameMap.width; ++x) {
        avergStrength += gameMap.getLocation(x, y).getSite().strength;
      }
    }
    avergStrength = avergStrength / (gameMap.width * gameMap.height) + 1;
    Networking.sendInit("Atom");
    while (true) {
      List<Move> moves = new ArrayList<Move>();

      Networking.updateFrame(gameMap);

      /* Pt fiecare celula care e a mea calculez urm. mutare */
      for (int y = 0; y < gameMap.height; ++y) {
        for (int x = 0; x < gameMap.width; ++x) {
          final Location location = gameMap.getLocation(x, y);
          final Site site = location.getSite();
          if (site.owner == myID) {
            moves.add(
                new Move(location, getNextMove(avergProduction, avergStrength,
                                               myID, site, location, gameMap)));
          }
        }
      }
      Networking.sendFrame(moves);
    }
  }
  /* metoda ce intoarce cea mai buna mutare in functie de locatia curenta */
  private static Direction getNextMove(int avergProduction, int avergStrength,
                                       int myID, Site mysite, Location myLoc,
                                       GameMap map) {
    /* pozitiile tuturor inamicilor de pe harta */
    ArrayList<Location> enemies = new ArrayList<>();
    for (int y = 0; y < map.height; ++y) {
      for (int x = 0; x < map.width; ++x) {
        Location loc = map.getLocation(x, y);
        Site sit = loc.getSite();
        if (sit.owner != 0 && sit.owner != myID)
          enemies.add(loc);
      }
    }

    /* locatiile neutre cu cel mai bun raport de prod/strength */
    ArrayList<Location> bestLocations = new ArrayList<>();
    double goodCell = avergProduction / (double)avergStrength;
    for (int y = 0; y < map.height; ++y) {
      for (int x = 0; x < map.width; ++x) {
        Location l = map.getLocation(x, y);
        Site s = l.getSite();
        if (s.owner == 0 && heuristic(l, map) > 1.25 * goodCell)
          bestLocations.add(l);
      }
    }
    /* locatia cea mai apropiata care face parte din bestLocations */
    Location closestGoodLoc = bestLocations.get(0);
    double bestLocDistance = map.getDistance(bestLocations.get(0), myLoc);
    for (Location loc : bestLocations) {
      double d = map.getDistance(loc, myLoc);
      if (d < bestLocDistance) {
        closestGoodLoc = loc;
        bestLocDistance = d;
      }
    }
    /* cel mai apropiat inamic */
    Location closestNME = enemies.get(0);
    double bestNMEDistance = map.getDistance(enemies.get(0), myLoc);
    for (Location loc : enemies) {
      double d = map.getDistance(loc, myLoc);
      if (d < bestNMEDistance) {
        closestNME = loc;
        bestNMEDistance = d;
      }
    }
    Direction direction;
    /* in functie de cea mai apropiata mutare (fie acolo un inamic sau o celula
     * neutra) aleg sa merg la cea mai buna celula */
    if (map.getDistance(closestGoodLoc, myLoc) <
        map.getDistance(closestNME, myLoc))
      direction = goHere(closestGoodLoc, myLoc, map, myID);
    else
      direction = goHere(closestNME, myLoc, map, myID);
    /* verific daca nu cumva exista o mutare mai buna din punct de vedere al
     * productiei/strength */
    ArrayList<Direction> bestNextMoves = new ArrayList<Direction>();
    for (Direction d : Direction.CARDINALS) {
      Location tempLoc = map.getLocation(myLoc, d);
      Site tempSite = tempLoc.getSite();
      /* daca se indeplineste conditia adaug celula intr o lista */
      if (tempSite.owner == 0 &&
          heuristic(tempLoc, map) > (1.25 * avergProduction) / avergStrength)
        bestNextMoves.add(d);
    }
    /* verific daca din cele mai bune miscari exista una care merita / se poate
     * sa fie cucerita */
    if (bestNextMoves.size() > 1)
      direction = getBestMove(myLoc, map, bestNextMoves);
    Location neighbor = map.getLocation(myLoc, direction);
    Site neighSite = neighbor.getSite();
    /* daca celula vecina e mai puternica sau daca strength-ul meu e mai
     * mic decat 5*productia celulei curente aleg sa astep */
    if (neighSite.owner != myID && neighSite.strength >= mysite.strength ||
        mysite.strength < 5 * mysite.production || mysite.strength < 25)
      direction = Direction.STILL;
    /* verific daca exista un manunchi inamic in vreo regiune din apropiere,
     * daca da aleg sa ma duc inspre el */
    if (countNMies(3, myID, myLoc, map) >= 2 &&
            mysite.strength > 5 * mysite.production ||
        countNMies(3, myID, myLoc, map) >= 2 && mysite.strength > 25) {
      int NMies;
      Direction dir = Direction.STILL;
      for (Direction d : Direction.CARDINALS) {
        NMies = 0;
        Location loc = myLoc;
        Site s = map.getSite(loc, d);
        for (int i = 3; i > 0; --i) {
          loc = map.getLocation(loc, d);
          s = map.getSite(loc);
          if (s.owner != 0 && s.owner != myID)
            NMies++;
        }
        if (NMies > 0) {
          dir = d;
        }
      }
      direction = dir;
    }
    return direction;
  }
  private static Direction goHere(Location dest, Location myLoc, GameMap map,
                                  int myID) {
    double distance = map.getDistance(dest, myLoc);
    ArrayList<Direction> directions = new ArrayList<Direction>();
    /* caut din aproape in aproape drumul cel mai scurt catre destinatie */
    for (Direction d : Direction.CARDINALS) {
      double dist = map.getDistance(dest, map.getLocation(myLoc, d));
      if (dist < distance)
        directions.add(d);
    }
    /* daca combinarea a 2 celule ale mele nu face overflow aleg sa o fac */
    for (Direction d : directions) {
      Location target = map.getLocation(myLoc, d);
      if (target.getSite().owner == myID &&
          target.getSite().strength + myLoc.getSite().strength <= 255)
        return d;
    }
    /* verific cea mai buna mutare */
    Direction dir = getBestMove(myLoc, map, directions);
    /* daca riscul e prea mare pt. a ataca */
    Site target = map.getLocation(myLoc, dir).getSite();
    if (target.strength + myLoc.getSite().strength >=
        255 + 2 * target.production) {
      /* incerc a gasi o celula neutra de cucerit */
      ArrayList<Direction> NCells = new ArrayList<Direction>();
      for (Direction d : Direction.CARDINALS) {
        Location neighbor = map.getLocation(myLoc, d);
        if (myID != neighbor.getSite().owner &&
            myLoc.getSite().strength > neighbor.getSite().strength)
          NCells.add(d);
      }
      /* daca nu se gaseste, se asteapta */
      if (NCells.isEmpty())
        dir = Direction.STILL;
      else
        dir = getBestMove(myLoc, map, NCells);
    }
    return dir;
  }

  /* metoda alege cea mai buna mutare dintr un vector de mutari pt. o celula
   * data ca parametru in functie de euristica */
  private static Direction getBestMove(Location location, GameMap map,
                                       ArrayList<Direction> bestNextMoves) {
    int best = 0;
    for (int i = 0; i < bestNextMoves.size(); ++i) {
      if (heuristic(map.getLocation(location, bestNextMoves.get(best)), map) <
          heuristic(map.getLocation(location, bestNextMoves.get(i)), map))
        best = i;
    }
    return bestNextMoves.get(best);
  }

  /* metoda numara inamicii dintr o regiune a mapei */
  private static int countNMies(int area, int myID, Location myLoc,
                                GameMap map) {
    int enemies = 0;
    for (Direction d : Direction.CARDINALS) {
      Location loc = myLoc;
      Site sit = map.getSite(loc, d);
      for (int i = area; i > 0; --i) {
        loc = map.getLocation(loc, d);
        sit = map.getSite(loc);
        if (sit.owner != 0 && sit.owner != myID)
          enemies++;
      }
    }
    return enemies;
  }

  private static double heuristic(Location location, GameMap map) {
    Site site = location.getSite();
    /* daca celula este neutra intorc prod/str pentru a alege cea mai buna
     * celula neutra in acest sens */
    if (site.owner == 0 && site.strength > 0)
      return site.production / (double)site.strength;
    if (site.owner == 0 && site.strength == 0)
      return site.production;
    /* aleg atacul in functie de risc si alti inamici care ar putea da join la
     * fight */
    int cap = 0;
    for (Direction d : Direction.CARDINALS) {
      Site s = map.getSite(location, d);
      if (s.owner != 0 && s.owner != site.owner) {
        cap += site.strength;
      }
    }
    return cap;
  }
}