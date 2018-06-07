package delta.games.lotro.tools.lore.items.complements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

import delta.games.lotro.lore.items.Armour;
import delta.games.lotro.lore.items.ArmourType;
import delta.games.lotro.lore.items.Item;
import delta.games.lotro.lore.items.ItemBinding;
import delta.games.lotro.lore.items.ItemPropertyNames;

/**
 * Injector for factory comments.
 * @author DAM
 */
public class FactoryCommentsInjector
{
  private static final Logger _logger=Logger.getLogger(FactoryCommentsInjector.class);

  private HashMap<Integer,Item> _items;

  /**
   * Constructor.
   * @param items Items to use.
   */
  public FactoryCommentsInjector(HashMap<Integer,Item> items)
  {
    _items=items;
  }

  /**
   * Perform injection.
   */
  public void doIt()
  {
    new NorthernMirkwoodItems(this).doIt();
    new MordorKeeperOfMysteriesItems(this).doIt();
    new MordorHighEnchanter(this).doIt();
  }

  /**
   * Inject a factory comment into the items with the given ids.
   * @param comment Comment to add.
   * @param ids IDs of targeted items.
   */
  public void injectComment(String comment, int[] ids)
  {
    for(int id : ids)
    {
      Item item=_items.get(Integer.valueOf(id));
      if (item!=null)
      {
        item.setProperty(ItemPropertyNames.FACTORY_COMMENT,comment);
      }
      else
      {
        _logger.warn("Item not found: ID="+id);
      }
    }
  }

  /**
   * Inject a binding into the items with the given ids.
   * @param binding Binding to set.
   * @param ids IDs of targeted items.
   */
  public void injectBinding(ItemBinding binding, int[] ids)
  {
    for(int id : ids)
    {
      Item item=_items.get(Integer.valueOf(id));
      if (item!=null)
      {
        item.setBinding(binding);
      }
      else
      {
        _logger.warn("Item not found: ID="+id);
      }
    }
  }

  /**
   * Inject an armour type into the items with the given ids.
   * @param armourType Armour type to set.
   * @param ids IDs of targeted items.
   */
  public void injectArmourType(ArmourType armourType, int[] ids)
  {
    for(int id : ids)
    {
      Item item=_items.get(Integer.valueOf(id));
      if (item!=null)
      {
        if (item instanceof Armour)
        {
          ((Armour)item).setArmourType(armourType);
        }
      }
      else
      {
        _logger.warn("Item not found: ID="+id);
      }
    }
  }

  /**
   * Share the stats between all the items designed by the given identifiers.
   * @param ids Identifiers to use.
   */
  public void shareStats(int[] ids)
  {
    Item source=null;
    List<Item> items=new ArrayList<Item>();
    for(int id : ids)
    {
      Item item=_items.get(Integer.valueOf(id));
      if (item!=null)
      {
        int nbStats=item.getStats().getStatsCount();
        if (nbStats==0)
        {
          items.add(item);
        }
        else
        {
          source=item;
        }
      }
    }
    if (source!=null)
    {
      for(Item item : items)
      {
        item.setItemLevel(source.getItemLevel());
        item.setEssenceSlots(source.getEssenceSlots());
        item.getStats().setStats(source.getStats());
        if ((item instanceof Armour) && (source instanceof Armour))
        {
          ((Armour)item).setArmourValue(((Armour)source).getArmourValue());
        }
      }
    }
  }
}
