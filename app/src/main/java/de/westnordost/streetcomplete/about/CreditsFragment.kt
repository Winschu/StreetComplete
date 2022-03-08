package de.westnordost.streetcomplete.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.FragmentCreditsBinding
import de.westnordost.streetcomplete.databinding.RowCreditsTranslatorsBinding
import de.westnordost.streetcomplete.ktx.getYamlObject
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.ktx.viewLifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.sufficientlysecure.htmltextview.HtmlTextView
import java.util.Locale

private typealias TranslationCreditMap = MutableMap<String, MutableMap<String, Int>>

/** Shows the credits of this app */
class CreditsFragment : Fragment(R.layout.fragment_credits) {

    private val binding by viewBinding(FragmentCreditsBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleScope.launch {
            addContributorsTo(readMainContributors(), binding.mainCredits)
            addContributorsTo(readProjectsContributors(), binding.projectsCredits)
            addContributorsTo(readArtContributors(), binding.artCredits)
            addContributorsTo(readCodeContributors(), binding.codeCredits)

            val inflater = LayoutInflater.from(view.context)
            for ((language, translators) in readTranslators()) {
                val itemBinding = RowCreditsTranslatorsBinding.inflate(inflater, binding.translationCredits, false)
                itemBinding.language.text = language
                itemBinding.contributors.text = translators
                binding.translationCredits.addView(itemBinding.root)
            }
        }

        binding.authorText.setHtml("Tobias Zwick (<a href=\"https://github.com/westnordost\">westnordost</a>)")

        binding.contributorMore.setHtml(getString(R.string.credits_contributors))
    }

    override fun onStart() {
        super.onStart()
        activity?.setTitle(R.string.about_title_authors)
    }

    private fun addContributorsTo(contributors: List<String>, view: ViewGroup) {
        val items = contributors.joinToString("") { "<li>$it</li>" }
        val textView = HtmlTextView(activity)
        TextViewCompat.setTextAppearance(textView, R.style.TextAppearance_Body)
        textView.setTextIsSelectable(true)
        textView.setHtml("<ul>$items</ul>")
        view.addView(textView, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }

    private suspend fun readMainContributors() = withContext(Dispatchers.IO) {
        resources.getYamlObject<List<Contributor>>(R.raw.credits_main).map { it.toTextWithLink() }
    }

    private suspend fun readProjectsContributors() = withContext(Dispatchers.IO) {
        resources.getYamlObject<List<String>>(R.raw.credits_projects)
    }

    private suspend fun readCodeContributors() = withContext(Dispatchers.IO) {
        // because they are mentioned as "main contributors" already
        val skipUsers = setOf("westnordost", "FloEdelmann", "matkoniecz", "ENT8R")
        resources.getYamlObject<List<Contributor>>(R.raw.credits_contributors)
            .filter { it.githubUsername !in skipUsers && it.score >= 50 }
            .sortedByDescending { it.score }
            .map { it.toTextWithLink() } + getString(R.string.credits_and_more)
    }

    private suspend fun readArtContributors() = withContext(Dispatchers.IO) {
        resources.getYamlObject<List<String>>(R.raw.credits_art)
    }

    private suspend fun readTranslators() = withContext(Dispatchers.IO) {
        val map = resources.getYamlObject<TranslationCreditMap>(R.raw.credits_translators)

        // skip those translators who contributed less than 2% of the translation
        for (contributors in map.values) {
            val totalTranslated = contributors.values.sum()
            val removedAnyone = contributors.values.removeAll { 100 * it / totalTranslated < 2 }
            if (removedAnyone) {
                contributors[""] = 1
            }
        }
        // skip plain English. That's not a translation
        map.remove("en")

        val languageTagByName = map.keys.associateBy { tag ->
            val locale = Locale.forLanguageTag(tag)
            locale.getDisplayName(locale)
        }
        val namesSorted = languageTagByName.keys.toList().sorted()

        namesSorted.associateWith { name ->
            val contributionCountByName = map[languageTagByName[name]]!!
            contributionCountByName.entries
                .sortedByDescending { it.value }
                .joinToString(", ") { it.key }
                .replace(Regex(", $"), " " + getString(R.string.credits_and_more))
        }
    }
}

private val Contributor.score: Int get() =
    linesOfCodeChanged + linesOfInterfaceMarkupChanged / 5 + assetFilesChanged * 15

private fun Contributor.toTextWithLink(): String = when (githubUsername) {
    null -> name
    name -> "<a href=\"https://github.com/$githubUsername\">$githubUsername</a>"
    else -> "$name (<a href=\"https://github.com/$githubUsername\">$githubUsername</a>)"
}

@Serializable
private data class Contributor(
    val name: String,
    val githubUsername: String? = null,
    val linesOfCodeChanged: Int = 0,
    val linesOfInterfaceMarkupChanged: Int = 0,
    val assetFilesChanged: Int = 0
)
