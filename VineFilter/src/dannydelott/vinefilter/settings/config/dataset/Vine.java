package dannydelott.vinefilter.settings.config.dataset;

import dannydelott.vinefilter.Messages;
import dannydelott.vinefilter.settings.Setup;
import dannydelott.vinefilter.settings.config.dataset.GrammarDependency;
import dannydelott.vinefilter.settings.filter.FieldType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class Vine {

	// ///////////////////
	// GLOBAL VARIABLES //
	// ///////////////////

	// holds the raw json object
	private JsonObject object;

	// holds the good filters
	private JsonArray goodFilters;

	private String id;
	private String url;
	private String text;
	private String scrubbedText;
	private List<TaggedToken> taggedTokens;
	private List<GrammarDependency> grammarDependencies;

	// error flag
	private boolean flagVine;

	// //////////////////////
	// FACTORY CONSTRUCTOR //
	// //////////////////////

	public static Vine newInstance(JsonObject j) {
		Vine v = new Vine(j);
		if (v.getFlagVine()) {
			return null;
		}

		return v;
	}

	// //////////////
	// CONSTRUCTOR //
	// //////////////

	private Vine(JsonObject j) {
		object = j;
		parseVine();

		// holds the good filters from the evaluation
		goodFilters = new JsonArray();

	}

	// /////////////////
	// PUBLIC METHODS //
	// /////////////////

	/**
	 * Returns {@code true} if the Vine contains the grammar dependency.
	 * 
	 * @param name
	 *            name of the grammar dependency
	 * @return true if vine contains the grammar dependency.<br />
	 *         false if not
	 */
	public boolean containsGrammarDependency(String name) {

		for (GrammarDependency gd : grammarDependencies) {
			if (gd.getRelation().contentEquals(name)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Returns {@code true} if {@code TaggedToken t} exists in the
	 * {@code taggedTokens}.
	 * 
	 * @param t
	 *            TaggedToken object
	 * @return {@code true} if t exists in {@code taggedTokens}
	 */
	public boolean containsTaggedToken(TaggedToken t) {
		if (taggedTokens.contains(t)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Returns {@code true} if the {@code targetWord} exists in a grammar
	 * dependency. This method takes into account target word n-grams of any
	 * size (eg: "pepper", "bell pepper", "green bell pepper") and checks
	 * against the tagged tokens in the vine.
	 * 
	 * @param targetWord
	 *            target word string
	 * @return {@code true} when target word exists in Vine
	 */
	public boolean containsTargetWordInGrammarDependency(String word,
			GrammarDependency rel, FieldType ft) {

		boolean doContinue = false;

		// --------------------------------
		// 1. Splits target word into words
		// --------------------------------

		String[] words = word.split(" ");
		String rootWord = words[words.length - 1];

		// holds the evaluations performed in step 4
		Boolean[] wordEvaluations = new Boolean[words.length];
		Arrays.fill(wordEvaluations, false);

		// ---------------------------------------------
		// 2. Checks for root word in grammar dependency
		// ---------------------------------------------

		if (containsStringInGrammarDependency(rootWord, rel, ft)) {
			wordEvaluations[words.length - 1] = true;
			doContinue = true;

		}

		if (!doContinue) {
			return false;
		}

		// ------------------------------------------------
		// 3. Checks for amod and nn relations of root word
		// ------------------------------------------------

		if (words.length > 1) {

			List<GrammarDependency> amod = getGrammarDependencyByName("amod");
			List<GrammarDependency> nn = getGrammarDependencyByName("nn");

			// 1. checks adjective modifier grammar relations
			for (GrammarDependency adjective : amod) {

				// checks words to the left of the root word
				for (int i = 0; i < words.length - 1; i++) {

					// sets evaluation to true if word is amod of root word
					if (containsStringInGrammarDependency(words[i], adjective,
							FieldType.GRAMMAR_DEPENDENCY_DEPENDENT)
							&& containsStringInGrammarDependency(rootWord,
									adjective,
									FieldType.GRAMMAR_DEPENDENCY_GOVERNOR)) {
						wordEvaluations[i] = true;
					}

				}
			}

			// 2. checks noun compound modifier grammar relations
			for (GrammarDependency noun : nn) {

				// checks words to the left of the root word
				for (int i = 0; i < words.length - 1; i++) {

					// sets evaluation to true if word is nn of root word
					if (containsStringInGrammarDependency(words[i], noun,
							FieldType.GRAMMAR_DEPENDENCY_DEPENDENT)
							&& containsStringInGrammarDependency(rootWord,
									noun, FieldType.GRAMMAR_DEPENDENCY_GOVERNOR)) {
						wordEvaluations[i] = true;
					}

				}
			}
		}

		// --------------------------
		// 4. Checks evaluation array
		// --------------------------

		for (boolean b : wordEvaluations) {
			if (!b) {
				return false;
			}
		}

		return true;

	}

	/**
	 * Returns a list of tagged tokens from the specified grammar dependencies
	 * and field type (ie: GRAMMAR_DEPENDENCY_GOVEROR or
	 * GRAMMAR_DEPENDENCY_DEPENDENT).
	 * 
	 * @param gd
	 *            List of grammar dependencies to get tagged tokens from
	 * @return
	 */
	public List<TaggedToken> getTaggedTokenFromGrammarDependency(
			List<GrammarDependency> gd, FieldType ft) {

		List<TaggedToken> result = new ArrayList<TaggedToken>();
		TaggedToken taggedToken = null;

		for (GrammarDependency dependency : gd) {

			// gets pos-tagged token from grammar dependency field
			switch (ft) {
			case GRAMMAR_DEPENDENCY_GOVERNOR:
				taggedToken = dependency
						.getTaggedTokenByFieldType(FieldType.GRAMMAR_DEPENDENCY_GOVERNOR);
				break;

			case GRAMMAR_DEPENDENCY_DEPENDENT:
				taggedToken = dependency
						.getTaggedTokenByFieldType(FieldType.GRAMMAR_DEPENDENCY_DEPENDENT);
				break;
			default:
				break;
			}

			// adds taggedToken to result
			if (taggedToken != null) {
				result.add(taggedToken);
			}
		}

		return result;
	}

	/**
	 * Returns a list of GrammarDependency objects according to the name in the
	 * relation field.
	 * 
	 * @param name
	 *            name of the grammar dependency (eg: "dobj")
	 * @return List of GrammarDependency objects called name
	 */
	public List<GrammarDependency> getGrammarDependencyByName(String name) {

		List<GrammarDependency> results = new ArrayList<GrammarDependency>();

		for (GrammarDependency g : grammarDependencies) {
			if (g.getRelation().contentEquals(name)) {
				results.add(g);
			}
		}

		return results;
	}

	/**
	 * Returns {@code true} if the text contains the exact string (case
	 * insensitive).
	 * 
	 * Example: string => "#dog" returns false if text contains "#doghouse".
	 * 
	 * @param string
	 * @return true if text contains the strict string
	 */
	public boolean containsStrictString(String string, boolean scrubbed) {

		String[] words = null;

		if (scrubbed) {
			words = scrubbedText.split(" ");
		} else if (!scrubbed) {
			words = text.split(" ");
		}

		// checks words for strict string
		for (String word : words) {
			if (word.toLowerCase().contentEquals(string.toLowerCase())) {
				return true;
			}
		}

		return false;

	}

	// //////////////////
	// PRIVATE METHODS //
	// //////////////////

	// parses vine
	private void parseVine() {

		// holds the current JSON value
		JsonValue temp;

		// resets error flag
		flagVine = false;

		// gets the vine object
		if (object == null) {
			flagVine = true;
			return;
		}

		// -------------
		// 1.
		// PARSES FIELDS
		// -------------

		// 1. "id"
		temp = object.get("id");
		if (temp == null) {
			System.out.println(Messages.Vine_errorId);
			flagVine = true;
			return;
		} else if (temp.isString()) {
			id = temp.asString();
		} else {
			System.out.println(Messages.Vine_errorId);
			flagVine = true;
			return;
		}

		// 2. "url"
		temp = object.get("url");
		if (temp == null) {
			System.out.println(Messages.Vine_errorUrl);
			flagVine = true;
			return;
		} else if (temp.isString()) {
			url = temp.asString();
		} else {
			System.out.println(Messages.Vine_errorUrl);
			flagVine = true;
			return;
		}

		// 3. "text"
		temp = object.get("text");
		if (temp == null) {
			System.out.println(Messages.Vine_errorText);
			flagVine = true;
			return;
		} else if (temp.isString()) {
			text = temp.asString();
		} else {
			System.out.println(Messages.Vine_errorText);
			flagVine = true;
			return;
		}

		// 4. "scrubbed_text"
		temp = object.get("scrubbed_text");
		if (temp == null) {
			System.out.println(Messages.Vine_errorScrubbedText);
			flagVine = true;
			return;
		} else if (temp.isString()) {
			scrubbedText = temp.asString();
		} else {
			System.out.println(Messages.Vine_errorScrubbedText);
			flagVine = true;
			return;
		}

		// 5. "pos_tags"
		temp = object.get("pos_tags");
		if (temp == null) {
			System.out.println(Messages.Vine_errorPosTags);
			flagVine = true;
			return;
		} else if (temp.isArray()) {

			// sets posTags list of TaggedToken objects
			taggedTokens = convertJsonArrayToListOfTaggedTokens(temp.asArray());
			if (taggedTokens == null) {
				System.out.println(Messages.Vine_errorPosTags);
				flagVine = true;
				return;
			}

		} else {
			System.out.println(Messages.Vine_errorPosTags);
			flagVine = true;
			return;
		}

		// 6. "grammar_dependencies"
		temp = object.get("grammar_dependencies");
		if (temp == null) {
			System.out.println(Messages.Vine_errorGrammarDependencies);
			flagVine = true;
			return;
		} else if (temp.isArray()) {

			// sets grammarDependencies list of GrammarDependency objects
			grammarDependencies = convertJsonArrayToListOfGrammarDependencies(temp
					.asArray());
			if (grammarDependencies == null) {
				System.out.println(Messages.Vine_errorGrammarDependencies);
				flagVine = true;
				return;
			}

		} else {
			System.out.println(Messages.Vine_errorGrammarDependencies);
			flagVine = true;
			return;
		}

	}

	// builds pos tags
	private List<TaggedToken> convertJsonArrayToListOfTaggedTokens(JsonArray j) {

		// the list to return
		List<TaggedToken> list = new ArrayList<TaggedToken>();

		// the JsonArray as List<String>
		List<String> temp = Setup.convertJsonArrayToStringList(j);

		if (temp == null) {
			return null;
		}
		String tempTag;
		String tempToken;
		String[] tempString;

		// loops over temp and creates TaggedToken entries
		for (String s : temp) {

			try {
				// splits the tag and the token
				// NOTE: splits into 2 segments
				tempString = s.split("-", 2);
				tempTag = tempString[0];
				tempToken = tempString[1];

				boolean added = list.add(new TaggedToken(tempTag, tempToken));
				if (!added) {
					return null;
				}

			} catch (PatternSyntaxException e) {
				e.printStackTrace();
				return null;
			} catch (ArrayIndexOutOfBoundsException e) {
				System.out.println(s);
				e.printStackTrace();
				return null;
			}
		}

		return list;

	}

	// builds grammar dependencies
	private List<GrammarDependency> convertJsonArrayToListOfGrammarDependencies(
			JsonArray j) {

		// the list to return
		List<GrammarDependency> list = new ArrayList<GrammarDependency>();

		// the JsonArray as List<String>
		List<String> temp = Setup.convertJsonArrayToStringList(j);
		if (temp == null) {
			return null;
		}

		String tempRelation = null;
		String tempGovToken = null;
		int tempGovPosition = 0;
		EnumeratedToken tempGovEnumeratedToken;

		String tempDepToken = null;
		int tempDepPosition = 0;
		EnumeratedToken tempDepEnumeratedToken;

		String[] tempString = null;

		for (String s : temp) {

			// the grammar relation string to parse
			// System.out.println(s);

			try {

				// ---------------------
				// GETS GRAMMAR RELATION
				// ---------------------

				// [0] => "nn"
				tempString = s.split("\\(");

				// assigns the first array element as grammar relation
				tempRelation = tempString[0];

				// reconstructs the relation
				// System.out.print(tempRelation + "(");

				// -------------------
				// GETS GOVERNOR TOKEN
				// -------------------

				// [1] => "prices-0,oil-1)"
				// NOTE: limits split to 2 segments
				tempString = s.split("\\(", 2);

				// [0] => "prices"
				tempString = tempString[1].split("-[0-9]+[']*,");

				// assigns first array element as the governor token
				tempGovToken = tempString[0];

				// reconstructs the governor token
				// System.out.print(tempGovToken + "-");

				// ----------------------
				// GETS GOVERNOR POSITION
				// ----------------------

				// [1] => "0,oil-1)"
				tempString = s.split("\\Q" + tempGovToken + "-\\E");

				// [0] => "0"
				// NOTE: Splits at one or more single quotes followed by a comma
				tempString = tempString[1].split("[']*,");

				// assigns first array element to governor position
				tempGovPosition = Integer.parseInt(tempString[0]);

				// reconstructs the governor position
				// System.out.print(tempGovPosition + ",");

				// -------------------------------
				// MAKES GOVERNOR ENUMURATED TOKEN
				// -------------------------------

				tempGovEnumeratedToken = new EnumeratedToken(tempGovToken,
						tempGovPosition);

				// --------------------
				// GETS DEPENDENT TOKEN
				// --------------------

				// [0] => "nn(prices-0,oil"
				tempString = s.split("[-]+[0-9]+[\\)]");

				// [1] => "oil"
				tempString = tempString[0].split(",");

				// assigns the final array element as the dependent token
				tempDepToken = tempString[tempString.length - 1];

				// reconstructs the dependent token
				// System.out.print(tempDepToken + "-");

				// -----------------------
				// GETS DEPENDENT POSITION
				// -----------------------

				// tmp => "nn(prices-0,oil-1
				String tmp = s.replaceAll("[']*[\\)]", "");

				// [1] => "1"
				tempString = tmp.split("-"); // [1] = "oil-1"

				// assigns the final array element as the dependent position
				tempDepPosition = Integer
						.parseInt(tempString[tempString.length - 1]);

				// reconstructs the dependent position
				// System.out.println(tempDepPosition + ")");

				// --------------------------------
				// MAKES DEPENDENT ENUMERATED TOKEN
				// --------------------------------

				// makes dependent enumerated token
				tempDepEnumeratedToken = new EnumeratedToken(tempDepToken,
						tempDepPosition);

				// creates Grammar Dependency object
				boolean added = list.add(new GrammarDependency(this,
						tempRelation, tempGovEnumeratedToken,
						tempDepEnumeratedToken));
				if (!added) {
					return null;
				}

			} catch (PatternSyntaxException e) {
				e.printStackTrace();
				return null;
			} catch (NumberFormatException e) {
				e.printStackTrace();
				return null;
			} catch (ArrayIndexOutOfBoundsException e) {
				e.printStackTrace();
				return null;
			}
		}
		return list;
	}

	private boolean containsStringInGrammarDependency(String s,
			GrammarDependency gd, FieldType ft) {

		boolean result = false;

		switch (ft) {

		case GRAMMAR_DEPENDENCY_GOVERNOR:
			if (gd.governor.getToken().contentEquals(s)) {
				result = true;
			}

			break;
		case GRAMMAR_DEPENDENCY_DEPENDENT:
			if (gd.dependent.getToken().contentEquals(s)) {
				result = true;
			}
			break;

		default:
			break;
		}

		return result;

	}

	// /////////////////
	// GLOBAL SETTERS //
	// /////////////////

	public void addGoodFilter(String filterName) {
		goodFilters.add(filterName);
	}

	// /////////////////
	// GLOBAL GETTERS //
	// /////////////////

	public boolean getFlagVine() {
		return flagVine;
	}

	public JsonObject getJsonObject() {
		return object;
	}

	public JsonArray getGoodFilters() {
		return goodFilters;
	}

	public String getId() {
		return id;
	}

	public String getUrl() {
		return url;
	}

	public String getText() {
		return text;
	}

	public String getScrubbedText() {
		return scrubbedText;
	}

	public List<TaggedToken> getTaggedTokens() {
		return taggedTokens;
	}

	public List<GrammarDependency> getGrammarDependencies() {
		return grammarDependencies;
	}

}
