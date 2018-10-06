package delta.games.lotro.tools.lore.recipes.lotrowiki;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Segment;
import net.htmlparser.jericho.Source;
import net.htmlparser.jericho.StartTag;

import org.apache.log4j.Logger;

import delta.common.utils.NumericTools;
import delta.common.utils.text.TextTools;
import delta.games.lotro.common.Duration;
import delta.games.lotro.lore.crafting.recipes.CraftingResult;
import delta.games.lotro.lore.crafting.recipes.Ingredient;
import delta.games.lotro.lore.crafting.recipes.Recipe;
import delta.games.lotro.lore.crafting.recipes.RecipeVersion;
import delta.games.lotro.lore.items.Item;
import delta.games.lotro.lore.items.ItemProxy;
import delta.games.lotro.tools.lore.items.ItemsResolver;
import delta.games.lotro.tools.utils.JerichoHtmlUtils;
import delta.games.lotro.tools.utils.lotrowiki.LotroWikiConstants;
import delta.games.lotro.tools.utils.lotrowiki.LotroWikiSiteInterface;
import delta.games.lotro.utils.Escapes;

/**
 * Parser for lotro-wiki recipes index pages.
 * @author DAM
 */
public class LotroWikiRecipeIndexPageParser
{
  private static final Logger _logger=Logger.getLogger(LotroWikiRecipeIndexPageParser.class);

  private static final String INDEX="/index.php/";
  private static final String INDEX_MISSING_PAGE_START="/index.php?title=";
  private static final String INDEX_MISSING_PAGE_END="&action=edit";

  private int _tier;
  private LotroWikiSiteInterface _lotroWiki;
  private ItemsResolver _resolver;

  /**
   * Constructor.
   * @param lotroWiki Lotro-wiki interface.
   */
  public LotroWikiRecipeIndexPageParser(LotroWikiSiteInterface lotroWiki)
  {
    _lotroWiki=lotroWiki;
    _resolver=new ItemsResolver();
  }

  /**
   * Handle a recipe index category.
   * @param indexId Index identifier.
   * @param tier Current tier.
   * @return a list of loaded recipes.
   */
  public List<Recipe> doRecipesIndex(String indexId, int tier)
  {
    _tier=tier;
    String url=LotroWikiConstants.BASE_URL+"/index.php/"+indexId;
    String filename=Escapes.escapeFile(indexId)+"/main.html";
    File recipesIndexFile=_lotroWiki.download(url,filename);
    System.out.println(recipesIndexFile);
    List<Recipe> recipes=parseRecipesIndexPage(recipesIndexFile);
    return recipes;
  }

  /**
   * Parse a lotro-wiki recipes index page.
   * @param from Source page.
   * @return loaded recipes.
   */
  private List<Recipe> parseRecipesIndexPage(File from)
  {
    List<Recipe> recipes=null;
    try
    {
      FileInputStream inputStream=new FileInputStream(from);
      Source source=new Source(inputStream);
      recipes=parseTables(source);
    }
    catch(Exception e)
    {
      _logger.error("Cannot parse recipes index page ["+from+"]",e);
    }
    return recipes;
  }

  private List<Recipe> parseTables(Source source)
  {
    List<Recipe> recipes=new ArrayList<Recipe>();
    Element root=JerichoHtmlUtils.findElementByTagNameAndAttributeValue(source,HTMLElementName.DIV,"class","mw-parser-output");
    if (root!=null)
    {
      List<Element> children=root.getChildElements();
      String category=null;
      for(Element child : children)
      {
        String tagName=child.getStartTag().getName();
        if (HTMLElementName.H2.equals(tagName))
        {
          category=JerichoHtmlUtils.getTextFromTag(child);
          //System.out.println("=== Category: "+category+" ===");
        }
        else if (HTMLElementName.TABLE.equals(tagName))
        {
          if (checkTable(child))
          {
            List<Recipe> parsedRecipes=parseTable(child);
            for(Recipe parsedRecipe : parsedRecipes)
            {
              parsedRecipe.setCategory(category);
            }
            recipes.addAll(parsedRecipes);
          }
        }
      }
    }
    return recipes;
  }

  private List<Recipe> parseTable(Element table)
  {
    List<Recipe> recipes=new ArrayList<Recipe>();
    List<Element> rows=JerichoHtmlUtils.findElementsByTagName(table,HTMLElementName.TR);
    // Remove header
    Element headerRow=rows.remove(0);
    Integer xpColumnIndex=findColumnByName(headerRow,"XP");
    Integer resultIndex=findColumnByName(headerRow,"Craft Item");
    Integer componentsIndex=findColumnByName(headerRow,"Components");
    Integer recipeIndex=findColumnByName(headerRow,"Recipe");
    for(Element row : rows)
    {
      //System.out.println("-- Recipe --");
      Recipe recipe=handleRow(row,xpColumnIndex,resultIndex,componentsIndex,recipeIndex);
      recipes.add(recipe);
    }
    return recipes;
  }

  private boolean checkTable(Element table)
  {
    String tableClass=table.getStartTag().getAttributeValue("class");
    return "altRowsMed topTable".equals(tableClass);
  }

  private Integer findColumnByName(Element headerRow, String headerPart)
  {
    Integer ret=null;
    List<Element> headerCells=JerichoHtmlUtils.findElementsByTagName(headerRow,HTMLElementName.TH);
    int index=0;
    for(Element headerCell : headerCells)
    {
      String headerText=JerichoHtmlUtils.getTextFromTag(headerCell);
      if (headerText.contains(headerPart))
      {
        ret=Integer.valueOf(index);
      }
      index++;
    }
    return ret;
  }

  private Recipe handleRow(Element row, Integer xpIndex, Integer resultIndex, Integer componentsIndex, Integer recipeIndex)
  {
    if (resultIndex==null)
    {
      return null;
    }
    Recipe recipe=new Recipe();
    List<Element> cells=JerichoHtmlUtils.findElementsByTagName(row,HTMLElementName.TD);
    if (cells.size()>=4)
    {
      // Results
      Element resultsCell=cells.get(resultIndex.intValue());
      List<ItemInfos> resultsInfo=parseItems(resultsCell,false);
      List<RecipeVersion> results=buildResults(resultsInfo);
      recipe.setVersions(results);
      // Name
      if (results.size()>0)
      {
        String name=results.get(0).getRegular().getItem().getName();
        recipe.setName(name);
      }
      Element typeCell=cells.get(recipeIndex.intValue());
      parseRecipeCell(typeCell,recipe);
      // Ingredients
      Element ingredientsCell=cells.get(componentsIndex.intValue());
      List<ItemInfos> ingredientsInfo=parseItems(ingredientsCell,false);
      List<Ingredient> ingredients=buildIngredients(ingredientsInfo);
      recipe.setIngredients(ingredients);
      if (xpIndex!=null)
      {
        Element xpCell=cells.get(xpIndex.intValue());
        String xpStr=JerichoHtmlUtils.getTextFromTag(xpCell).trim();
        if ((xpStr.length()>0) && (!"?".equals(xpStr)))
        {
          Integer xp=NumericTools.parseInteger(xpStr);
          if (xp!=null)
          {
            recipe.setXP(xp.intValue());
          }
        }
        else
        {
          System.out.println("No XP");
        }
      }
    }
    return recipe;
  }

  private void parseRecipeCell(Element recipeCell, Recipe recipe)
  {
    String contents=JerichoHtmlUtils.getTextFromTag(recipeCell).trim();
    //System.out.println("{"+contents+"}");
    String[] lines=contents.split("\n");
    for(String line : lines)
    {
      line=line.trim();
      if (line.length()==0) continue;
      if (line.endsWith("Recipe")) line=line.substring(0,line.length()-6).trim();
      if (line.length()==0) continue;
      // Types
      if (line.contains("Basic")) System.out.println("Basic");
      else if (line.equals("Vendor")) System.out.println("Vendor");
      else if (line.equals("Reputation")) System.out.println("Reputation");
      else if (line.equals("Reputation Vendor")) System.out.println("Reputation");
      else if (line.equals("Barter")) System.out.println("Barter");
      else if (line.equals("Festival")) System.out.println("Festival Vendor");
      else if ((line.startsWith("former Spring Festival drop")) || (line.equals("obsolete Spring Festival recipe")) || (line.equals("obsolete Spring Festival item")))
      {
        System.out.println("Festival Drop");
        System.out.println("Obsolete");
      }
      else if (line.equals("Guild")) System.out.println("Guild");
      else if (line.equals("Drop")) System.out.println("Drop");
      else if (line.equals("Quest")) System.out.println("Quest");
      else if (line.equals("Obsolete")) System.out.println("Obsolete");
      // Single use?
      else if ((line.contains("Single Use")) || (line.contains("Single use")))
      {
        //System.out.println("One time use");
        recipe.setOneTimeUse(true);
      }
      // Cooldown
      else if ((line.contains("d ")) || (line.endsWith("h")) || (line.endsWith("d")))
      {
        Integer cooldown=Duration.parseDurationString(line);
        //System.out.println("Cooldown: "+Duration.getDurationString(cooldown.intValue()));
        recipe.setCooldown(cooldown.intValue());
      }
      else if (line.contains("day"))
      {
        int spaceIndex=line.indexOf(" ");
        Integer nbDays=NumericTools.parseInteger(line.substring(0,spaceIndex));
        if (nbDays!=null)
        {
          int cooldown=nbDays.intValue()*Duration.DAY;
          //System.out.println("Cooldown: "+Duration.getDurationString(cooldown));
          recipe.setCooldown(cooldown);
        }
      }
      else if (line.contains("week"))
      {
        int spaceIndex=line.indexOf(" ");
        Integer nbWeeks=NumericTools.parseInteger(line.substring(0,spaceIndex));
        if (nbWeeks!=null)
        {
          int cooldown=nbWeeks.intValue()*Duration.DAY*7;
          //System.out.println("Cooldown: "+Duration.getDurationString(cooldown));
          recipe.setCooldown(cooldown);
        }
      }
      else
      {
        System.out.println("Unmanaged line ["+line+"]");
      }
    }
  }

  private Integer _count;
  private ItemProxy _itemId;

  private List<ItemInfos> parseItems(Element cell, boolean critical)
  {
    List<ItemInfos> items=new ArrayList<ItemInfos>();
    Segment content=cell.getContent();
    List<Element> childElements=cell.getChildElements();
    for(Iterator<Segment> it=content.getNodeIterator();it.hasNext();)
    {
      Segment node=it.next();
      //<span style="font-size: 1em;">
      if (node.getClass()==Segment.class)
      {
        String text=node.toString();
        Integer count=NumericTools.parseInteger(text,false);
        if (count!=null)
        {
          _count=count;
        }
      }
      else if (node instanceof StartTag)
      {
        StartTag startTag=(StartTag)node;
        Element child=startTag.getElement();
        if (childElements.contains(child))
        {
          String tagName=startTag.getName();
          if (HTMLElementName.SPAN.equals(tagName))
          {
            // Count:
            // <span style="position: absolute">
            Element countTag=JerichoHtmlUtils.findElementByTagNameAndAttributeValue(child,HTMLElementName.SPAN,"style","position: absolute");
            if (countTag!=null)
            {
              _count=parseItemCount(countTag);
            }
            items.addAll(parseItems(child,false));
          }
          else if (HTMLElementName.I.equals(tagName))
          {
            _count=null;
            _itemId=null;
            items.addAll(parseItems(child,true));
          }
          else if (HTMLElementName.A.equals(tagName))
          {
            // Check if it is an icon link
            Element img=JerichoHtmlUtils.findElementByTagName(child,HTMLElementName.IMG);
            if (img==null)
            {
              _itemId=parseItemIdFromLink(child);
              Integer count=parseItemCountFromItemText(child);
              if (count!=null)
              {
                _count=count;
              }
              ItemInfos newItem=new ItemInfos(_itemId,_count,critical);
              items.add(newItem);
              //showItem(_itemId,_count,critical);
              _count=null;
              _itemId=null;
            }
          }
        }
      }
    }
    _count=null;
    _itemId=null;
    return items;
  }

  private void fixItem(ItemProxy proxy)
  {
    String name=proxy.getName();
    if ("Glazed Leather".equals(name))
    {
      if (_tier==7) name="Glazed Calenard Leather";
    }
    if ("Brushed Leather".equals(name))
    {
      if (_tier==7) name="Brushed Calenard Leather";
    }
    if ("Finished Leather".equals(name))
    {
      if (_tier==7) name="Finished Calenard Leather";
    }
    if ("Rune-keeper's Stone of the First Age".equals(name))
    {
      name="Rune-keeper's Rune-stone of the First Age";
    }
    if (name.endsWith("Ingot"))
    {
      name=name.replace("Khazad","Khazâd");
    }
    if (name.endsWith("Shavings")) name=name.substring(0,name.length()-1);
    if (name.equals("Captain's Greatsword of the First Age"))
    {
      name="Reshaped Captain's Greatsword of the First Age";
    }
    if (name.equals("Captain's Greatsword of the Second Age"))
    {
      name="Reforged Captain's Greatsword of the Second Age";
    }
    if (name.equals("Champion's Greatsword of the First Age"))
    {
      name="Reshaped Champion's Greatsword of the First Age";
    }
    if (name.equals("Champion's Greatsword of the Second Age"))
    {
      name="Reforged Champion's Greatsword of the Second Age";
    }
    if (name.equals("Guardian's Greatsword of the First Age"))
    {
      name="Reshaped Guardian's Greatsword of the First Age";
    }
    if (name.equals("Guardian's Greatsword of the Second Age"))
    {
      name="Reforged Guardian's Greatsword of the Second Age";
    }
    if ((name.endsWith("Axe of the Westfold")) ||
        (name.endsWith("Bow of the Westfold")) ||
        (name.endsWith("Club of the Westfold")) ||
        (name.endsWith("Crossbow of the Westfold")) ||
        (name.endsWith("Dagger of the Westfold")) ||
        (name.endsWith("Hammer of the Westfold")) ||
        (name.endsWith("Mace of the Westfold")) ||
        (name.endsWith("Sword of the Westfold")))
    {
      name=name.replace("of the Westfold","of the Westemnet");
    }
    if ((name.endsWith(" of the Resolute")) && (name.contains("Premium")))
    {
      name=name.replace("of the Resolute","of Resolve");
    }
    if (name.equals("Anórien Assault Circlet")) name="Anórien Assault Helm";
    if (name.equals("Anórien Campaign Circlet")) name="Anórien Campaign Helm";
    if (name.equals("Anórien Battle Shield")) name="Anórien Battle-shield";
    if (name.equals("Anórien Skirmish Shield")) name="Anórien Skirmish-shield";
    if (name.equals("Commanding Battle-shield of the Westfold")) name="Commanding Battle-shield of the Westemnet";
    if (name.equals("Defensive Battle-shield of the Westfold")) name="Defensive Battle-shield of the Westemnet";
    if (name.equals("Forceful Skirmish-shield of the Westfold")) name="Forceful Skirmish-shield of the Westemnet";
    if (name.equals("Watchful Skirmish-shield of the Westfold")) name="Watchful Skirmish-shield of the Westemnet";
    if (name.equals("Spirited Campaign-shield of the Westfold")) name="Spirited Campaign-shield of the Westemnet";
    if (name.equals("Steadfast Campaign-shield of the Westfold")) name="Steadfast Campaign-shield of the Westemnet";

    if (name.equals("Ancient Steel Greatsword")) proxy.setId(1879188146);

    // Burglar stuff
    if (name.equals("Conviction Signal (Artisan)")) proxy.setId(1879102738);
    if (name.equals("Conviction Signal (Master)")) proxy.setId(1879102739);
    if (name.equals("Conviction Signal (Supreme)")) proxy.setId(1879154023);
    if (name.equals("Tactics Signal (Artisan)")) proxy.setId(1879102735);
    if (name.equals("Tactics Signal (Master)")) proxy.setId(1879102736);
    if (name.equals("Tactics Signal (Supreme)")) proxy.setId(1879154022);
    if (name.equals("Guile Signal (Artisan)")) proxy.setId(1879102744);
    if (name.equals("Guile Signal (Master)")) proxy.setId(1879102745);
    if (name.equals("Guile Signal (Supreme)")) proxy.setId(1879154025);
    if (name.equals("Strength Signal (Artisan)")) proxy.setId(1879102741);
    if (name.equals("Strength Signal (Master)")) proxy.setId(1879102742);
    if (name.equals("Strength Signal (Supreme)")) proxy.setId(1879154024);

    if (name.equals("Battle Bow of Théodred")) name="War Bow of Théodred";
    if (name.equals("Battle Crossbow of Théodred")) name="War Crossbow of Théodred";
    if (name.equals("War Bow of the Rider")) name="Battle Bow of the Rider";
    if (name.equals("War Crossbow of the Rider")) name="Battle Crossbow of the Rider";
    if (name.equals("Vibrant War Shield of the Rider")) name="Vibrant Battle Shield of the Rider";
    if (name.equals("Strong War Shield of the Rider")) name="Strong Battle Shield of the Rider";

    // Cope with Westfold Metalsmith riffler recipes
    // - level 70
    if (name.equals("Calenard Riffler")) name="Westfold Riffler";
    if (name.equals("Exceptional Calenard Riffler")) name="Exceptional Westfold Riffler";
    if (name.equals("Calenard Riffler of Hope (Crafted)")) name="Westfold Riffler of Hope";
    if (name.equals("Exceptional Calenard Riffler of Hope")) name="Exceptional Westfold Riffler of Hope";
    if (name.equals("Calenard Riffler of Writs")) name="Westfold Riffler of Writs";
    if (name.equals("Exceptional Calenard Riffler of Writs")) name="Exceptional Westfold Riffler of Writs";
    // - level 75
    if (name.equals("Superior Calenard Riffler")) name="Calenard Riffler";
    if (name.equals("Exceptional Superior Calenard Riffler")) name="Exceptional Calenard Riffler";
    if (name.equals("Superior Calenard Riffler of Hope")) name="Calenard Riffler of Hope";
    if (name.equals("Exceptional Superior Calenard Riffler of Hope")) name="Exceptional Calenard Riffler of Hope";
    if (name.equals("Superior Calenard Riffler of Writs")) name="Calenard Riffler of Writs";
    if (name.equals("Exceptional Superior Calenard Riffler of Writs")) name="Exceptional Calenard Riffler of Writs";
    // same with chisels
    // - level 70
    if (name.equals("Calenard Chisel")) name="Westfold Chisel";
    if (name.equals("Exceptional Calenard Chisel")) name="Exceptional Westfold Chisel";
    if (name.equals("Calenard Chisel of Fire")) name="Westfold Chisel of Fire";
    if (name.equals("Exceptional Calenard Chisel of Fire")) name="Exceptional Westfold Chisel of Fire";
    if (name.equals("Calenard Chisel of Lightning")) name="Westfold Chisel of Lightning";
    if (name.equals("Exceptional Calenard Chisel of Lightning")) name="Exceptional Westfold Chisel of Lightning";
    // - level 75
    if (name.equals("Superior Calenard Chisel")) name="Calenard Chisel";
    if (name.equals("Exceptional Superior Calenard Chisel")) name="Exceptional Calenard Chisel";
    if (name.equals("Superior Calenard Chisel of Fire")) name="Calenard Chisel of Fire";
    if (name.equals("Exceptional Superior Calenard Chisel of Fire")) name="Exceptional Calenard Chisel of Fire";
    if (name.equals("Superior Calenard Chisel of Lightning")) name="Calenard Chisel of Lightning";
    if (name.equals("Exceptional Superior Calenard Chisel of Lightning")) name="Exceptional Calenard Chisel of Lightning";

    // Cope with Eastemnet Metalsmith riffler recipes
    // - level 80
    if (name.equals("Riddermark Riffler")) name="Eastemnet Riffler";
    if (name.equals("Exceptional Riddermark Riffler")) name="Exceptional Eastemnet Riffler";
    if (name.equals("Riddermark Riffler of Hope")) name="Eastemnet Riffler of Hope";
    if (name.equals("Exceptional Riddermark Riffler of Hope")) name="Exceptional Eastemnet Riffler of Hope";
    if (name.equals("Riddermark Riffler of Writs")) name="Eastemnet Riffler of Writs";
    if (name.equals("Exceptional Riddermark Riffler of Writs")) name="Exceptional Eastemnet Riffler of Writs";
    // - level 85
    if (name.equals("Superior Riddermark Riffler")) name="Riddermark Riffler";
    if (name.equals("Exceptional Superior Riddermark Riffler")) name="Exceptional Riddermark Riffler";
    if (name.equals("Superior Riddermark Riffler of Hope")) name="Riddermark Riffler of Hope";
    if (name.equals("Exceptional Superior Riddermark Riffler of Hope")) name="Exceptional Riddermark Riffler of Hope";
    if (name.equals("Superior Riddermark Riffler of Writs")) name="Riddermark Riffler of Writs";
    if (name.equals("Exceptional Superior Riddermark Riffler of Writs")) name="Exceptional Riddermark Riffler of Writs";
    // same with chisels
    // - level 80
    if (name.equals("Riddermark Chisel")) name="Eastemnet Chisel";
    if (name.equals("Exceptional Riddermark Chisel")) name="Exceptional Eastemnet Chisel";
    if (name.equals("Riddermark Chisel of Fire")) name="Eastemnet Chisel of Fire";
    if (name.equals("Exceptional Riddermark Chisel of Fire")) name="Exceptional Eastemnet Chisel of Fire";
    if (name.equals("Riddermark Chisel of Lightning")) name="Eastemnet Chisel of Lightning";
    if (name.equals("Exceptional Riddermark Chisel of Lightning")) name="Exceptional Eastemnet Chisel of Lightning";
    // - level 85
    if (name.equals("Superior Riddermark Chisel")) name="Riddermark Chisel";
    if (name.equals("Exceptional Superior Riddermark Chisel")) name="Exceptional Riddermark Chisel";
    if (name.equals("Superior Riddermark Chisel of Fire")) name="Riddermark Chisel of Fire";
    if (name.equals("Exceptional Superior Riddermark Chisel of Fire")) name="Exceptional Riddermark Chisel of Fire";
    if (name.equals("Superior Riddermark Chisel of Lightning")) name="Riddermark Chisel of Lightning";
    if (name.equals("Exceptional Superior Riddermark Chisel of Lightning")) name="Exceptional Riddermark Chisel of Lightning";

    // Oils
    if (name.equals("Riddermark Fire-oil")) name="Eastemnet Fire-oil";
    if (name.equals("Riddermark Light-oil")) name="Eastemnet Light-oil";

    if (name.equals("Crisp Roast Duck")) name="Crisp Roast Duck and Potato";
    if (name.endsWith("Sabercat")) name=name.replace("Sabercat","Sabre-cat");
    if (name.equals("Gondorian Weapon Parts")) name="Set of Gondorian Weapon Parts";
    if (name.equals("Gondorian Woodcrafts")) name="Set of Gondorian Woodcrafts";
    if (name.equals("Piece of Sealed Wax")) name="Piece of Eastemnet Sealed Wax";

    proxy.setName(name);
  }

  private List<RecipeVersion> buildResults(List<ItemInfos> resultsInfo)
  {
    List<RecipeVersion> ret=new ArrayList<RecipeVersion>();

    RecipeVersion currentVersion=null;
    for(ItemInfos itemInfo : resultsInfo)
    {
      CraftingResult result=new CraftingResult();
      result.setItem(itemInfo._item);
      result.setQuantity(itemInfo._quantity);
      if (itemInfo._critical)
      {
        result.setCriticalResult(true);
        if (currentVersion!=null)
        {
          currentVersion.setCritical(result);
        }
        else
        {
          System.out.println("Warn: no regular result! Critical result="+itemInfo);
        }
      }
      else
      {
        currentVersion=new RecipeVersion();
        ret.add(currentVersion);
        currentVersion.setRegular(result);
      }
    }
    return ret;
  }

  private List<Ingredient> buildIngredients(List<ItemInfos> ingredientsInfo)
  {
    List<Ingredient> ret=new ArrayList<Ingredient>();

    for(ItemInfos itemInfo : ingredientsInfo)
    {
      if (itemInfo._item!=null)
      {
        Ingredient ingredient=new Ingredient();
        ingredient.setItem(itemInfo._item);
        ingredient.setQuantity(itemInfo._quantity);
        ingredient.setOptional(itemInfo._critical);
        ret.add(ingredient);
      }
    }
    return ret;
  }

  private ItemProxy parseItemIdFromLink(Element aTag)
  {
    ItemProxy ret=null;
    // Item
    String itemKey=null;
    String itemName=null;
    // Regular page link
    // <a href="/index.php/Item:Rowan_Campfire_Kit" title="Item:Rowan Campfire Kit">
    // Missing page link:
    // <a href="/index.php?title=Item:Ploughman%27s_Loaf&amp;action=edit&amp;redlink=1" class="new" title="Item:Ploughman&#39;s Loaf (page does not exist)">Item:Ploughman's Loaf</a>
    String link=aTag.getAttributeValue("href");
    if (link!=null)
    {
      String missingLink=TextTools.findBetween(link,INDEX_MISSING_PAGE_START,INDEX_MISSING_PAGE_END);
      if (missingLink!=null)
      {
        itemKey=missingLink;
        itemName=JerichoHtmlUtils.getTextFromTag(aTag);
        if (itemName.startsWith("Item:")) itemName=itemName.substring(5);
        ret=new ItemProxy();
        ret.setItemKey(itemKey);
        ret.setName(itemName);
      }
      else
      {
        if (link.startsWith(INDEX))
        {
          itemKey=link.substring(INDEX.length());
          itemName=aTag.getAttributeValue("title");
          if (itemName.startsWith("Item:")) itemName=itemName.substring(5);
        }
      }
    }
    if ((itemKey!=null) && (!itemKey.startsWith("Item:")))
    {
      if ((itemKey.endsWith("_Recipe_Index")) || ("Fish".equals(itemKey)))
      {
        // Ignore
      }
      else
      {
        System.out.println("Warn: bad item key: "+itemKey);
      }
    }
    else
    {
      ret=new ItemProxy();
      ret.setItemKey(itemKey);
      ret.setName(itemName);
      fixItem(ret);
      resolveItem(ret);
    }
    return ret;
  }

  private void resolveItem(ItemProxy itemProxy)
  {
    String name=itemProxy.getName();
    int itemId=itemProxy.getId();
    if (itemId==0)
    {
      itemId=resolveByName(name);
    }
    String icon=null;
    if (itemId==0)
    {
      Item item=_resolver.getItem(name);
      if (item!=null)
      {
        itemId=item.getIdentifier();
        icon=item.getIcon();
      }
    }
    if (itemId!=0)
    {
      itemProxy.setId(itemId);
      itemProxy.setIcon(icon);
      itemProxy.setItemKey(null);
    }
    else
    {
      System.out.println("Item not found [" + name + "]");
    }
  }

  private int resolveByName(String name)
  {
    int itemId=0;
    /*
    if ("Armour (Wastes)".equals(name)) itemId=1879341924;
    else if ("Bag of Flower Petals".equals(name)) itemId=1879199971;
    else if ("Black Steel Key".equals(name)) itemId=1879356039;
    else if ("Broken Blade (Wastes)".equals(name)) itemId=1879342063;
    else if ("Flower Petals (Multi-use)".equals(name)) itemId=1879200102;
    else if ("Gold-bound_Lootbox".equals(name)) itemId=1879225083;
    else if ("Golden Token of the Riddermark".equals(name)) itemId=1879237278;
    else if ("Grant Golf Chip Emote".equals(name)) itemId=1879187356;
    else if ("Ivar's Helm".equals(name)) itemId=1879197561; //(Cosmetic)
    else if ("Letter (Rohan Awaits)".equals(name)) itemId=1879249134; //("Letter")
    else if ("Major Essence of Critical Rating".equals(name)) itemId=1879313417; // (assuming Tier7)
    else if ("Major Essence of Physical Mitigation".equals(name)) itemId=1879313525; // (assuming Tier7)
    else if ("Map of Eriador".equals(name)) itemId=1879205541;
    else if ("Metal Scrap (Wastes)".equals(name)) itemId=1879342064;
    else if ("Prized Ost Dunhoth War-steed".equals(name)) itemId=1879206179;
    else if ("Provisions (Wastes)".equals(name)) itemId=1879341934;
    else if ("Rotten Fruit (Multi-use)".equals(name)) itemId=1879200100;
    else if ("Rotten Fruit".equals(name)) itemId=1879199969;
    else if ("Salt (Wastes)".equals(name)) itemId=1879342065;
    else if ("Steed of Elessar's Host".equals(name)) itemId=1879345100;
    else if ("Sturdy Steel Key".equals(name)) itemId=1879227487; //1879223825 (fond bleu) or 1879227487 (fond jaune)(different icons)
    else if ("Universal Healing Potion".equals(name)) itemId=1879248609; //(Rejuvenation Potion)
    else if ("Upgrade Task Limit (+1)".equals(name)) itemId=1879201943; // or 1879201944, 1879201945, 1879201946
    else if ("Weapons (Wastes)".equals(name)) itemId=1879341942;
    */
    return itemId;
  }

  private Integer parseItemCount(Element countTag)
  {
    // Count
    Integer count=null;
    if (countTag!=null)
    {
      String countStr=JerichoHtmlUtils.getTextFromTag(countTag).trim();
      if (countStr.length()>0)
      {
        count=NumericTools.parseInteger(countStr);
      }
    }
    return count;
  }

  private Integer parseItemCountFromItemText(Element child)
  {
    Integer count=null;
    String text=JerichoHtmlUtils.getTextFromTag(child).trim();
    int firstSpaceIndex=text.indexOf(' ');
    if (firstSpaceIndex!=-1)
    {
      String countStr=text.substring(0,firstSpaceIndex);
      count=NumericTools.parseInteger(countStr,false);
    }
    return count;
  }

  private class ItemInfos
  {
    public ItemProxy _item;
    public int _quantity;
    public boolean _critical;

    public ItemInfos(ItemProxy item, Integer quantity, boolean critical)
    {
      _item=item;
      _quantity=(quantity!=null)?quantity.intValue():1;
      _critical=critical;
    }

    @Override
    public String toString()
    {
      StringBuilder sb=new StringBuilder();
      if (_critical)
      {
        sb.append("Critical: ");
      }
      if (_quantity>1)
      {
        sb.append(_quantity).append(' ');
      }
      String itemName=_item.getName();
      String itemKey=_item.getItemKey();
      sb.append(itemName).append(" (").append(itemKey).append(')');
      return sb.toString();
    }
  }
}
