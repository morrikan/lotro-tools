package delta.games.lotro.tools.lore.recipes.dat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import delta.games.lotro.dat.data.DataFacade;
import delta.games.lotro.dat.data.PropertiesSet;
import delta.games.lotro.lore.crafting.recipes.CraftingResult;
import delta.games.lotro.lore.crafting.recipes.Ingredient;
import delta.games.lotro.lore.crafting.recipes.Recipe;
import delta.games.lotro.lore.crafting.recipes.RecipeVersion;
import delta.games.lotro.lore.crafting.recipes.RecipesManager;
import delta.games.lotro.lore.items.Item;
import delta.games.lotro.lore.items.ItemProxy;
import delta.games.lotro.lore.items.ItemsManager;

/**
 * Get recipe definitions from DAT files.
 * @author DAM
 */
public class MainDatRecipesLoader
{
  private static final Logger LOGGER=Logger.getLogger(MainDatRecipesLoader.class);

  // Professions...
  private static final String[] PROFESSIONS={"Cook","Farmer","Forester","Jeweller","Metalsmith","Prospector","Scholar","Tailor","Weaponsmith","Woodworker"};
  private static final int[] INDEX={0x79003304,0x79003920,0x79003921,0x79001BC3,0x79001AE7,0x79003922,0x79001A62,0x79001C75,0x79001DA2,0x79001E45};

  private static final int CRAFTING_UI_CATEGORY=0x23000065;

  private DataFacade _facade;
  private Map<Integer,Integer> _xpMapping;
  private Map<Integer,Float> _cooldownMapping;

  /**
   * Constructor.
   * @param facade Data facade.
   */
  public MainDatRecipesLoader(DataFacade facade)
  {
    _facade=facade;
  }

  private Map<Integer,List<Integer>> loadProfessionIndex(int indexDataId)
  {
    Map<Integer,List<Integer>> ret=new HashMap<Integer,List<Integer>>();
    PropertiesSet properties=_facade.loadProperties(indexDataId);
    if (properties!=null)
    {
      Object[] tiersPropertiesGen=(Object[])properties.getProperty("CraftProfession_TierArray");
      for(Object tierPropertiesGen : tiersPropertiesGen)
      {
        PropertiesSet tierProperties=(PropertiesSet)tierPropertiesGen;
        Integer tier=(Integer)tierProperties.getProperty("CraftProfession_Tier");
        Object[] recipeDataIdsGen=(Object[])tierProperties.getProperty("CraftProfession_RecipeArray");
        if ((tier!=null) && (recipeDataIdsGen!=null))
        {
          List<Integer> recipeIds=new ArrayList<Integer>();
          for(Object recipeDataIdGen : recipeDataIdsGen)
          {
            recipeIds.add((Integer)recipeDataIdGen);
          }
          ret.put(tier,recipeIds);
        }
      }
    }
    return ret;
  }

  private Recipe load(int indexDataId)
  {
    Recipe recipe=null;
    int dbPropertiesId=indexDataId+0x09000000;
    PropertiesSet properties=_facade.loadProperties(dbPropertiesId);
    if (properties!=null)
    {
      //System.out.println(properties.dump());
      recipe=new Recipe();
      // ID
      recipe.setIdentifier(indexDataId);
      // Name
      String name=getStringProperty(properties,"CraftRecipe_Name");
      recipe.setName(name);
      // Category
      Integer categoryIndex=(Integer)properties.getProperty("CraftRecipe_UICategory");
      if (categoryIndex!=null)
      {
        String category=getCategory(categoryIndex.intValue());
        recipe.setCategory(category);
      }
      // XP
      Integer xpId=(Integer)properties.getProperty("CraftRecipe_XPReward");
      if (xpId!=null)
      {
        Integer xpValue=_xpMapping.get(xpId);
        if (xpValue!=null)
        {
          recipe.setXP(xpValue.intValue());
        }
      }
      // Cooldown
      Integer cooldownId=(Integer)properties.getProperty("CraftRecipe_CooldownDuration");
      if (cooldownId!=null)
      {
        Float cooldownValue=_cooldownMapping.get(cooldownId);
        if (cooldownValue!=null)
        {
          recipe.setCooldown(cooldownValue.intValue());
        }
      }
      // Single use
      Integer singleUse=(Integer)properties.getProperty("CraftRecipe_OneTimeRecipe");
      if ((singleUse!=null) && (singleUse.intValue()==1))
      {
        recipe.setOneTimeUse(true);
      }
      // Ingredients
      List<Ingredient> ingredients=getIngredientsList(properties,"CraftRecipe_IngredientList",false);
      // Optional ingredients
      List<Ingredient> optionalIngredients=getIngredientsList(properties,"CraftRecipe_OptionalIngredientList",true);
      // Results
      RecipeVersion firstResult=buildVersion(properties);
      firstResult.getIngredients().addAll(ingredients);
      firstResult.getIngredients().addAll(optionalIngredients);
      recipe.getVersions().add(firstResult);
      // Multiple output results
      Object[] multiOutput=(Object[])properties.getProperty("CraftRecipe_MultiOutputArray");
      if (multiOutput!=null)
      {
        for(Object output : multiOutput)
        {
          PropertiesSet outputProps=(PropertiesSet)output;
          RecipeVersion newVersion=firstResult.cloneData();

          // Patch
          // - result
          Integer resultId=(Integer)outputProps.getProperty("CraftRecipe_ResultItem");
          if ((resultId!=null) && (resultId.intValue()>0))
          {
            newVersion.getRegular().setItem(buildItemProxy(resultId.intValue()));
          }
          // - critical result
          Integer critResultId=(Integer)outputProps.getProperty("CraftRecipe_CriticalResultItem");
          if ((critResultId!=null) && (critResultId.intValue()>0))
          {
            CraftingResult critical=newVersion.getCritical();
            critical.setItem(buildItemProxy(critResultId.intValue()));
          }
          // Ingredient
          Integer ingredientId=(Integer)outputProps.getProperty("CraftRecipe_Ingredient");
          if (ingredientId!=null)
          {
            newVersion.getIngredients().get(0).setItem(buildItemProxy(ingredientId.intValue()));
          }
          recipe.getVersions().add(newVersion);
        }
      }

      // Profession
      Integer professionId=(Integer)properties.getProperty("CraftRecipe_Profession");
      if (professionId!=null)
      {
        String profession=getProfessionFromProfessionId(professionId.intValue());
        recipe.setProfession(profession);
      }
      // Tier
      Integer tier=getTier(properties);
      if (tier!=null)
      {
        recipe.setTier(tier.intValue());
      }
      // Fixes
      if (name==null)
      {
        name=recipe.getVersions().get(0).getRegular().getItem().getName();
        recipe.setName(name);
      }

      /*
      if (indexDataId==1879089025)
      {
        // Field recipes:
        // CraftRecipe_ResultItem gives the id of the field "item" (not found by LUA indexer)
        // same for the crit result: CraftRecipe_CriticalResultItem
        // Field icons:
        //   CraftRecipe_Field_CritResultIcon: 1091479564
        //   CraftRecipe_Field_ResultIcon: 1091479564

        System.out.println(properties.dump());
      }
      */
      Integer guild=(Integer)properties.getProperty("CraftRecipe_RequiredCraftGuild");
      if ((guild!=null) && (guild.intValue()!=0))
      {
        //System.out.println("guild: "+guild+" = "+name);
      }
    }
    else
    {
      LOGGER.warn("Could not handle recipe ID="+indexDataId);
    }
    return recipe;
  }

  private Integer getTier(PropertiesSet properties)
  {
    Integer tier=(Integer)properties.getProperty("CraftRecipe_Tier");
    if (tier==null)
    {
      Object[] tierArray=(Object[])properties.getProperty("CraftRecipe_TierArray");
      if (tierArray!=null)
      {
        tier=(Integer)(tierArray[0]);
      }
    }
    return tier;
  }

  private List<Ingredient> getIngredientsList(PropertiesSet properties, String propertyName, boolean optional)
  {
    List<Ingredient> ret=new ArrayList<Ingredient>();
    Object[] ingredientsGen=(Object[])properties.getProperty(propertyName);
    if (ingredientsGen!=null)
    {
      for(Object ingredientGen : ingredientsGen)
      {
        PropertiesSet ingredientProperties=(PropertiesSet)ingredientGen;
        // ID
        Integer ingredientId=(Integer)ingredientProperties.getProperty("CraftRecipe_Ingredient");
        // Quantity
        Integer quantity=(Integer)ingredientProperties.getProperty("CraftRecipe_IngredientQuantity");
        Ingredient ingredient=new Ingredient();
        if (quantity!=null)
        {
          ingredient.setQuantity(quantity.intValue());
        }
        // Build item proxy
        ItemProxy ingredientProxy=buildItemProxy(ingredientId.intValue());
        ingredient.setItem(ingredientProxy);
        // Optionals
        ingredient.setOptional(optional);
        if (optional)
        {
          Float critBonus=(Float)ingredientProperties.getProperty("CraftRecipe_IngredientCritBonus");
          if (critBonus!=null)
          {
            ingredient.setCriticalChanceBonus(Integer.valueOf((int)(critBonus.floatValue()*100)));
          }
        }
        ret.add(ingredient);
      }
    }
    return ret;
  }

  private RecipeVersion buildVersion(PropertiesSet properties)
  {
    RecipeVersion version=new RecipeVersion();
    // Regular result
    CraftingResult regular=new CraftingResult();
    {
      Integer resultId=(Integer)properties.getProperty("CraftRecipe_ResultItem");
      if (resultId!=null)
      {
        // Item
        regular.setItem(buildItemProxy(resultId.intValue()));
        // Quantity
        Integer quantity=(Integer)properties.getProperty("CraftRecipe_ResultItemQuantity");
        if (quantity!=null)
        {
          regular.setQuantity(quantity.intValue());
        }
      }
    }
    version.setRegular(regular);
    // Critical result
    CraftingResult criticalResult=null;
    Integer criticalResultId=(Integer)properties.getProperty("CraftRecipe_CriticalResultItem");
    if ((criticalResultId!=null) && (criticalResultId.intValue()>0))
    {
      criticalResult=new CraftingResult();
      criticalResult.setCriticalResult(true);
      // Item
      criticalResult.setItem(buildItemProxy(criticalResultId.intValue()));
      // Quantity
      Integer quantity=(Integer)properties.getProperty("CraftRecipe_CriticalResultItemQuantity");
      if (quantity!=null)
      {
        criticalResult.setQuantity(quantity.intValue());
      }
      version.setCritical(criticalResult);
      // Critical success chance
      Float critBonus=(Float)properties.getProperty("CraftRecipe_CriticalSuccessChance");
      if (critBonus!=null)
      {
        version.setBaseCriticalChance(Integer.valueOf((int)(critBonus.floatValue()*100)));
      }
    }
    return version;
  }

  private ItemProxy buildItemProxy(int id)
  {
    ItemsManager items=ItemsManager.getInstance();
    Item item=items.getItem(id);
    ItemProxy proxy=new ItemProxy();
    proxy.setItem(item);
    return proxy;
  }

  private String getStringProperty(PropertiesSet properties, String propertyName)
  {
    String ret=null;
    Object value=properties.getProperty(propertyName);
    if (value!=null)
    {
      if (value instanceof String[])
      {
        ret=((String[])value)[0];
      }
    }
    return ret;
  }

  private String getCategory(int key)
  {
    return _facade.getEnumsManager().resolveEnum(CRAFTING_UI_CATEGORY,key);
  }

  private String getProfessionFromProfessionId(int id)
  {
    int nbProfessions=PROFESSIONS.length;
    for(int i=0;i<nbProfessions;i++)
    {
      if (INDEX[i]-0x9000000==id) return PROFESSIONS[i];
    }
    return null;
  }

  private void doIt()
  {
    // XP mapping
    _xpMapping=loadXpMapping();
    // Cooldown mapping
    _cooldownMapping=loadCooldownMapping();
    RecipesManager recipesManager=new RecipesManager();
    //useIndexes(recipesManager);
    scanAll(recipesManager);
    int nbRecipes=recipesManager.getRecipesCount();
    System.out.println("Found: "+nbRecipes+" recipes.");
    File out=new File("../lotro-companion/data/lore/recipes_dat.xml");
    recipesManager.writeToFile(out);
  }

  private void scanAll(RecipesManager recipesManager)
  {
    int nb=0;
    Set<Integer> done=new HashSet<Integer>();
    List<Integer> recipeDataIds=loadIndexedRecipeIds();
    List<Integer> legacyDataIds=loadLorebookRecipeIds();
    Set<Integer> ids=new HashSet<Integer>();
    ids.addAll(recipeDataIds);
    ids.addAll(legacyDataIds);
    for(Integer recipeDataId : ids)
    {
      for(int i=recipeDataId.intValue()-10000;i<recipeDataId.intValue()+10000;i++)
      {
        Integer key=Integer.valueOf(i);
        if (!done.contains(key))
        {
          Recipe recipe=tryId(i);
          if (recipe!=null)
          {
            recipesManager.registerRecipe(recipe);
            nb++;
            System.out.println(i+" => "+nb);
          }
        }
        done.add(key);
      }
    }
  }

  private Recipe tryId(int id)
  {
    Recipe recipe=null;
    PropertiesSet props=_facade.loadProperties(id+0x09000000);
    if (props!=null)
    {
      Object category=props.getProperty("CraftRecipe_UICategory");
      if (category!=null)
      {
        recipe=load(id);
      }
    }
    return recipe;
  }

  /*
  private void useIndexes(RecipesManager recipesManager)
  {
    List<Integer> recipeDataIds=loadIndexedRecipeIds();
    for(Integer recipeDataId : recipeDataIds)
    {
      Recipe recipe=load(recipeDataId.intValue());
      if (recipe!=null)
      {
        recipesManager.registerRecipe(recipe);
      }
    }
  }
  */

  private List<Integer> loadLorebookRecipeIds()
  {
    RecipesManager manager=new RecipesManager();
    File fromFile=new File("data/recipes/resolvedLegacyRecipes.xml");
    manager.loadRecipesFromFile(fromFile);
    List<Integer> ret=new ArrayList<Integer>();
    for(Recipe recipe : manager.getAll())
    {
      int id=recipe.getIdentifier();
      if (id!=0)
      {
        ret.add(Integer.valueOf(id));
      }
    }
    Collections.sort(ret);
    return ret;
  }

  private List<Integer> loadIndexedRecipeIds()
  {
    List<Integer> ret=new ArrayList<Integer>();
    int nbProfessions=PROFESSIONS.length;
    for(int i=0;i<nbProfessions;i++)
    {
      // Load profession index
      int indexDataId=INDEX[i];
      if (indexDataId!=0)
      {
        Map<Integer,List<Integer>> map=loadProfessionIndex(indexDataId);
        for(Integer tier : map.keySet())
        {
          List<Integer> recipedDataIds=map.get(tier);
          ret.addAll(recipedDataIds);
        }
      }
    }
    return ret;
  }

  private Map<Integer,Integer> loadXpMapping()
  {
    Map<Integer,Integer> ret=new HashMap<Integer,Integer>();
    PropertiesSet properties=_facade.loadProperties(0x7900021E);
    if (properties!=null)
    {
      Object[] array=(Object[])properties.getProperty("CraftControl_XPRewardArray");
      for(int i=0;i<array.length;i++)
      {
        PropertiesSet item=(PropertiesSet)array[i];
        Integer key=(Integer)item.getProperty("CraftControl_XPRewardEnum");
        Integer value=(Integer)item.getProperty("CraftControl_XPRewardValue");
        ret.put(key,value);
      }
    }
    return ret;
  }

  private Map<Integer,Float> loadCooldownMapping()
  {
    Map<Integer,Float> ret=new HashMap<Integer,Float>();
    PropertiesSet properties=_facade.loadProperties(0x79000264);
    if (properties!=null)
    {
      Object[] array=(Object[])properties.getProperty("CooldownControl_DurationMapList");
      for(int i=0;i<array.length;i++)
      {
        PropertiesSet item=(PropertiesSet)array[i];
        Integer key=(Integer)item.getProperty("CooldownControl_DurationType");
        Float value=(Float)item.getProperty("CooldownControl_DurationValue");
        ret.put(key,value);
      }
    }
    return ret;
  }

  /**
   * Main method for this tool.
   * @param args Not used.
   */
  public static void main(String[] args)
  {
    DataFacade facade=new DataFacade();
    new MainDatRecipesLoader(facade).doIt();
    facade.dispose();
  }
}