package delta.games.lotro.tools.dat.travels;

import java.util.List;

import delta.games.lotro.dat.DATConstants;
import delta.games.lotro.dat.data.DataFacade;
import delta.games.lotro.dat.data.PropertiesSet;
import delta.games.lotro.dat.data.ui.UIElement;
import delta.games.lotro.dat.data.ui.UILayout;
import delta.games.lotro.dat.data.ui.UILayoutLoader;
import delta.games.lotro.tools.dat.utils.DatUtils;

/**
 * Loads travel NPC for the stables collection UI.
 * @author DAM
 */
public class MainDatStablesCollectionLoader
{
  private DataFacade _facade;

  /**
   * Constructor.
   * @param facade Data facade.
   */
  public MainDatStablesCollectionLoader(DataFacade facade)
  {
    _facade=facade;
  }

  private void doIt()
  {
    UILayout layout=new UILayoutLoader(_facade).loadUiLayout(0x220008BB);
    inspect(layout.getChildElements());
  }

  private void inspect(List<UIElement> uiElements)
  {
    for(UIElement uiElement : uiElements)
    {
      if (uiElement.getIdentifier()==268452723) // CollectionView_ME_Map_Buttons
      {
        inspectStablemasterButtons(uiElement);
        break;
      }
      inspect(uiElement.getChildElements());
    }
  }

  private void inspectStablemasterButtons(UIElement uiElement)
  {
    for(UIElement buttonElement : uiElement.getChildElements())
    {
      PropertiesSet props=buttonElement.getProperties();
      String[] names=(String[])props.getProperty("UICore_Element_tooltip_entry");
      String name=DatUtils.getFullString(names, ",");
      Integer npc=(Integer)props.getProperty("UI_StablesCollection_TravelNPC");
      System.out.println("Name: "+name+", NPC="+npc);
      handleNpc(npc.intValue());
    }
  }

  private void handleNpc(int npcId)
  {
    /*
TravelWebSellMultiplier: 1.0
TravelWebWC: 1879216497
Travel_DiscountArray: 
  #1: 268446908 (Discount_Travel_Special)
  #2: 268452164 (Discount_Travel_Theodred)
     */
    PropertiesSet props=_facade.loadProperties(npcId+DATConstants.DBPROPERTIES_OFFSET);
    Integer travelNodeId=(Integer)props.getProperty("TravelWebWC");
    System.out.println("Travel node ID: "+travelNodeId);
  }

  /**
   * Main method for this tool.
   * @param args Not used.
   */
  public static void main(String[] args)
  {
    DataFacade facade=new DataFacade();
    new MainDatStablesCollectionLoader(facade).doIt();
    facade.dispose();
  }
}
