package com.example.wajuscanner.ui.preview

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.wajuscanner.R
import com.example.wajuscanner.domain.model.Page

/** Satu halaman dokumen: pratonton thumbnail + tindakan (putar/OCR/duplikat/edit/padam). */
@Composable
fun PageCard(
    page: Page,
    pageNumber: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onOcr: () -> Unit = {},
    onRotate: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Column {
            page.thumbnailPath?.let { thumb ->
                val bitmap = BitmapFactory.decodeFile(thumb)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.preview_page_image, pageNumber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.preview_page_number, pageNumber),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onRotate) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = stringResource(R.string.cd_rotate_page)
                        )
                    }
                    IconButton(onClick = onOcr) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = stringResource(R.string.ocr_title)
                        )
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.preview_duplicate_page)
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.preview_edit_page)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.preview_delete_page)
                        )
                    }
                }
            }
        }
    }
}
