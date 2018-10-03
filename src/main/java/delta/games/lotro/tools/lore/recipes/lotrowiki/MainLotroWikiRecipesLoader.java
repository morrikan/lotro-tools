package delta.games.lotro.tools.lore.recipes.lotrowiki;

import java.io.File;
import java.util.List;

import delta.games.lotro.lore.crafting.recipes.Recipe;
import delta.games.lotro.lore.crafting.recipes.RecipesManager;
import delta.games.lotro.tools.utils.lotrowiki.LotroWikiSiteInterface;

/**
 * Download recipes data from the site lotro-wiki.
 * @author DAM
 */
public class MainLotroWikiRecipesLoader
{
  // Professions... Forester and Prospector not managed
  private static final String[] PROFESSIONS={"Cook","Farmer","Jeweller","Metalsmith","Scholar","Tailor","Weaponsmith","Woodworker"};
  private static final String[] TIERS={"Apprentice","Journeyman","Expert","Artisan","Master","Supreme","Westfold","Eastemnet","Westemnet","Anórien","Doomfold"};

  /**
   * Constructor.
   */
  public MainLotroWikiRecipesLoader()
  {
    // Nothing
  }

  private void doIt()
  {
    LotroWikiSiteInterface lotroWiki=new LotroWikiSiteInterface("recipes");

    // Recipe index parser
    LotroWikiRecipeIndexPageParser parser=new LotroWikiRecipeIndexPageParser(lotroWiki);

    RecipesManager recipesManager=new RecipesManager();
    for(String profession : PROFESSIONS)
    {
      for(String tier : TIERS)
      {
        String indexId=profession+"_"+tier+"_Recipe_Index";
        List<Recipe> recipes=parser.doRecipesIndex(indexId);
        for(Recipe recipe : recipes)
        {
          recipesManager.registerRecipe(recipe);
        }
      }
    }
    File out=new File("../lotro-companion/data/lore/recipes_wiki.xml");
    recipesManager.writeToFile(out);
  }

  /**
   * Main method for this tool.
   * @param args Not used.
   */
  public static void main(String[] args)
  {
    new MainLotroWikiRecipesLoader().doIt();
  }
}
