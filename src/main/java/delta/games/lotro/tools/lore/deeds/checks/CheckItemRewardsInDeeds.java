package delta.games.lotro.tools.lore.deeds.checks;

import java.util.List;
import java.util.Objects;

import delta.games.lotro.common.Rewards;
import delta.games.lotro.common.objects.ObjectItem;
import delta.games.lotro.common.objects.ObjectsSet;
import delta.games.lotro.lore.deeds.DeedDescription;
import delta.games.lotro.lore.items.Item;
import delta.games.lotro.lore.items.ItemsManager;

/**
 * Check consistency of item rewards in deeds.
 * @author DAM
 */
public class CheckItemRewardsInDeeds
{
  /**
   * Do it!
   * @param deeds Deeds to use.
   */
  public void doIt(List<DeedDescription> deeds)
  {
    for(DeedDescription deed : deeds)
    {
      handleDeed(deed);
    }
  }

  private void handleDeed(DeedDescription deed)
  {
    Rewards rewards=deed.getRewards();
    ObjectsSet objects=rewards.getObjects();
    int nbItems=objects.getNbObjectItems();
    for(int i=0;i<nbItems;i++)
    {
      ObjectItem objectItem=objects.getItem(i);
      int id=objectItem.getItemId();
      String name=objectItem.getName();
      Item item=ItemsManager.getInstance().getItem(id);
      if (item==null)
      {
        System.out.println("Item not found: id="+id+", name="+name);
      }
      else
      {
        String itemName=item.getName();
        if (!Objects.equals(name,itemName))
        {
          System.out.println("Fix item reward name from ["+name+"] to ["+itemName+"]");
          objectItem.setName(itemName);
        }
      }
    }
  }
}
