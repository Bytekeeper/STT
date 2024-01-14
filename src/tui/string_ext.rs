use unicode_segmentation::UnicodeSegmentation;

pub trait StringExt {
    fn remove_char(&mut self, idx: usize) -> char;
    fn insert_char(&mut self, idx: usize, ch: char);
    fn split_at_char(&mut self, mid: usize) -> (&str, &str);
    fn char_len(&self) -> usize;
    fn word_boundary_before(&self, at: usize) -> usize;
    fn word_boundary_after(&self, at: usize) -> usize;
}

impl StringExt for String {
    fn remove_char(&mut self, idx: usize) -> char {
        let byte_index = self
            .grapheme_indices(true)
            .nth(idx)
            .expect("Character to remove to be not beyond grapheme length")
            .0;
        self.remove(byte_index)
    }

    fn insert_char(&mut self, idx: usize, ch: char) {
        let byte_index = self
            .grapheme_indices(true)
            .nth(idx)
            .map(|i| i.0)
            .unwrap_or_else(|| self.len());
        self.insert(byte_index, ch)
    }

    fn split_at_char(&mut self, mid: usize) -> (&str, &str) {
        let byte_index = self
            .grapheme_indices(true)
            .nth(mid)
            .map(|i| i.0)
            .unwrap_or_else(|| self.len());
        self.split_at(byte_index)
    }

    fn char_len(&self) -> usize {
        self.graphemes(true).count()
    }

    fn word_boundary_before(&self, at: usize) -> usize {
        self.unicode_word_indices()
            .filter_map(|(pos, _)| if pos < at { Some(pos) } else { None })
            .max()
            .unwrap_or(0)
    }

    fn word_boundary_after(&self, at: usize) -> usize {
        self.unicode_word_indices()
            .filter_map(|(pos, _)| if pos > at { Some(pos) } else { None })
            .min()
            .unwrap_or(self.len())
    }
}
