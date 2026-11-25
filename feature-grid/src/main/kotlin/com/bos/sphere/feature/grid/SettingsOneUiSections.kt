package com.bos.sphere.feature.grid

/**
 * Wave 40 (settingsOneUI): the small grey section titles of a settings page.
 *
 * Wave 56 (settingsCore), CFG-31: a section starts at a row id, not at the English title of a row. Wave 57 (settingsTabs), CFG-40: the sections
 * are made where the rows are placed into the six tabs (the app's one placement table names every row of every section), and the category
 * carries them ([SettingCategory.headers]: row index to the translated header), so a header can neither name a row that is not there nor be lost
 * by a rename. The English-title table of the old category pages is gone with those pages.
 */
object OneUiSections {
    /** Item index to (translated) section title of [category]. */
    fun headers(category: SettingCategory): Map<Int, String> = category.headers.filterKeys { it in category.items.indices }
}
