package com.quantumff.segue.core.testing.compose

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/** Matches a node whose tap screen readers name [label], as in "Double-tap to [label]". */
fun hasClickLabel(label: String): SemanticsMatcher = SemanticsMatcher("click label is \"$label\"") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}
