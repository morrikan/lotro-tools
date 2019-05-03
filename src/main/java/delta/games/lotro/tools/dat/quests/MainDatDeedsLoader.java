package delta.games.lotro.tools.dat.quests;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import delta.common.utils.io.FileIO;
import delta.games.lotro.character.traits.TraitsManager;
import delta.games.lotro.common.CharacterClass;
import delta.games.lotro.common.Race;
import delta.games.lotro.common.rewards.Rewards;
import delta.games.lotro.dat.data.DataFacade;
import delta.games.lotro.dat.data.PropertiesSet;
import delta.games.lotro.dat.data.enums.EnumMapper;
import delta.games.lotro.lore.deeds.DeedDescription;
import delta.games.lotro.lore.deeds.DeedType;
import delta.games.lotro.tools.dat.GeneratedFiles;
import delta.games.lotro.tools.dat.characters.TraitLoader;
import delta.games.lotro.tools.dat.utils.DatUtils;
import delta.games.lotro.tools.lore.deeds.DeedsWriter;

/**
 * Get deeds definitions from DAT files.
 * @author DAM
 */
public class MainDatDeedsLoader
{
  private static final Logger LOGGER=Logger.getLogger(MainDatDeedsLoader.class);

  private static final int DEBUG_ID=1879000000;

  private DataFacade _facade;
  private Map<Integer,DeedDescription> _deeds;
  private EnumMapper _uiTab;

  private DatRewardsLoader _rewardsLoader;
  private DatObjectivesLoader _objectivesLoader;

  /**
   * Constructor.
   * @param facade Data facade.
   */
  public MainDatDeedsLoader(DataFacade facade)
  {
    _facade=facade;
    _deeds=new HashMap<Integer,DeedDescription>();
    _uiTab=_facade.getEnumsManager().getEnumMapper(587202588);
    _rewardsLoader=new DatRewardsLoader(facade);
    _objectivesLoader=new DatObjectivesLoader(facade);
  }

  private void load(int indexDataId)
  {
    DeedDescription deed=null;
    int dbPropertiesId=indexDataId+0x09000000;
    PropertiesSet properties=_facade.loadProperties(dbPropertiesId);
    if (properties!=null)
    {
      // Check
      boolean isQuest=DatQuestDeedsUtils.isQuest(properties);
      if (isQuest)
      {
        return;
      }
      if (indexDataId==DEBUG_ID)
      {
        FileIO.writeFile(new File(indexDataId+".props"),properties.dump().getBytes());
        System.out.println(properties.dump());
      }
      deed=new DeedDescription();
      // ID
      deed.setIdentifier(indexDataId);
      // Name
      String name=DatUtils.getStringProperty(properties,"Quest_Name");
      deed.setName(name);
      // Description
      String description=DatUtils.getStringProperty(properties,"Quest_Description");
      deed.setDescription(description);
      // UI Tab
      Integer uiTab=((Integer)properties.getProperty("Accomplishment_UITab"));
      String uiTabName=_uiTab.getString(uiTab.intValue());
      deed.setCategory(uiTabName);
      // Deed type
      handleDeedType(deed,properties);
      // Min level
      Integer minLevel=((Integer)properties.getProperty("Accomplishment_MinLevelToStart"));
      deed.setMinimumLevel(minLevel);
      // Challenge level
      //Integer challengeLevel=(Integer)properties.getProperty("Quest_ChallengeLevel");

      // Rewards
      Rewards rewards=deed.getRewards();
      _rewardsLoader.fillRewards(properties,rewards);

      // Objectives
      _objectivesLoader.handleObjectives(null,properties);

      // Web Store (needed xpack/region): WebStoreAccountItem_DataID

      _deeds.put(Integer.valueOf(deed.getIdentifier()),deed);
    }
    else
    {
      LOGGER.warn("Could not handle deed ID="+indexDataId);
    }
  }

  private void handleDeedType(DeedDescription deed, PropertiesSet properties)
  {
    DeedType type=null;
    Integer categoryId=((Integer)properties.getProperty("Accomplishment_Category"));
    if (categoryId!=null)
    {
      int typeCode=categoryId.intValue();
      if (typeCode==22)
      {
        type=DeedType.CLASS;
      }
      else if (typeCode==2)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.CAPTAIN);
      }
      else if (typeCode==3)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.GUARDIAN);
      }
      else if (typeCode==5)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.MINSTREL);
      }
      else if (typeCode==6)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.BURGLAR);
      }
      else if (typeCode==26)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.HUNTER);
      }
      else if (typeCode==28)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.CHAMPION);
      }
      else if (typeCode==30)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.LORE_MASTER);
      }
      else if (typeCode==35)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.WARDEN);
      }
      else if (typeCode==36)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.RUNE_KEEPER);
      }
      else if (typeCode==38)
      {
        type=DeedType.CLASS;
        deed.setRequiredClass(CharacterClass.BEORNING);
      }
      else if (typeCode==34)
      {
        type=DeedType.EVENT;
      }
      else if (typeCode==1)
      {
        type=DeedType.EXPLORER;
      }
      else if (typeCode==33)
      {
        type=DeedType.LORE;
      }
      else if (typeCode==25)
      {
        type=DeedType.RACE;
      }
      else if (typeCode==13)
      {
        type=DeedType.RACE;
        deed.setRequiredRace(Race.MAN);
      }
      else if (typeCode==21)
      {
        type=DeedType.RACE;
        deed.setRequiredRace(Race.ELF);
      }
      else if (typeCode==27)
      {
        type=DeedType.RACE;
        deed.setRequiredRace(Race.DWARF);
      }
      else if (typeCode==29)
      {
        type=DeedType.RACE;
        deed.setRequiredRace(Race.HOBBIT);
      }
      else if (typeCode==37)
      {
        type=DeedType.RACE;
        deed.setRequiredRace(Race.BEORNING);
      }
      else if (typeCode==11)
      {
        type=DeedType.REPUTATION;
      }
      else if (typeCode==20)
      {
        type=DeedType.SLAYER;
      }
      else
      {
        LOGGER.warn("Unmanaged type: "+typeCode);
      }
    }
    deed.setType(type);
  }

  private void doIt()
  {
    doScan();
    doIndex();
    doSave();
  }

  private void doScan()
  {
    for(int id=0x70000000;id<=0x77FFFFFF;id++)
    {
      boolean useIt=DatQuestDeedsUtils.isQuestOrDeedId(_facade,id);
      if (useIt)
      {
        load(id);
      }
    }
  }

  private void doSave()
  {
    int nbDeeds=_deeds.size();
    System.out.println("Nb deeds: "+nbDeeds);
    // Write deeds file
    boolean ok=DeedsWriter.writeSortedDeeds(new ArrayList<DeedDescription>(_deeds.values()),GeneratedFiles.DEEDS);
    if (ok)
    {
      System.out.println("Wrote deeds file: "+GeneratedFiles.DEEDS);
    }
    // Save traits
    TraitsManager traitsMgr=TraitsManager.getInstance();
    TraitLoader.saveTraits(traitsMgr);
  }

  void doIndex()
  {
    PropertiesSet deedsDirectory=_facade.loadProperties(0x79000255);
    //System.out.println(deedsDirectory.dump());
    Object[] list=(Object[])deedsDirectory.getProperty("Accomplishment_List");
    for(Object obj : list)
    {
      if (obj instanceof Integer)
      {
        load(((Integer)obj).intValue());
      }
      else if (obj instanceof Object[])
      {
        Object[] objs=(Object[])obj;
        for(Object obj2 : objs)
        {
          if (obj2 instanceof Integer)
          {
            load(((Integer)obj2).intValue());
          }
          else
          {
            System.out.println(obj.getClass());
          }
        }
      }
    }
  }

  /**
   * Main method for this tool.
   * @param args Not used.
   */
  public static void main(String[] args)
  {
    DataFacade facade=new DataFacade();
    new MainDatDeedsLoader(facade).doIt();
    facade.dispose();
  }
}
