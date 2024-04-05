use crate::tui::{
    component::{Consumed, EventHandler},
    string_ext::StringExt,
};
use crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use tui::{
    buffer::Buffer,
    layout::Rect,
    text::Line,
    widgets::{Paragraph, StatefulWidget, Widget},
    Frame,
};

#[derive(Default)]
pub struct TextFieldState {
    cursor: (usize, usize),
    scroll: (u16, u16),
    lines: Vec<String>,
    pub cursor_screen_pos: (u16, u16),
}

impl TextFieldState {
    pub fn clear(&mut self) {
        self.lines.clear();
        self.cursor = (0, 0);
        self.scroll = (0, 0);
    }

    pub fn has_text(&self) -> bool {
        self.lines.get(0).map(|l| !l.is_empty()).unwrap_or(false)
    }

    pub fn get_text(&self) -> String {
        self.lines.join("\n")
    }

    pub fn set_text(&mut self, text: &str) {
        self.lines = text.lines().map(|s| s.to_owned()).collect();
    }

    pub fn end(&mut self) {
        if let Some(last) = self.lines.last() {
            self.cursor = (last.char_len(), self.lines.len() - 1);
        } else {
            self.cursor = (0, 0);
        }
    }

    fn word_boundary_before(&self) -> usize {
        self.lines
            .get(self.cursor.1)
            .map(|s| s.word_boundary_before(self.cursor.0))
            .unwrap_or(0)
    }

    fn word_boundary_after(&self) -> usize {
        self.lines
            .get(self.cursor.1)
            .map(|s| s.word_boundary_after(self.cursor.0))
            .unwrap_or(0)
    }

    fn current_line_len(&self) -> usize {
        self.lines
            .get(self.cursor.1)
            .map(|l| l.char_len())
            .unwrap_or(0)
    }
}

pub struct TextField;

impl StatefulWidget for TextField {
    type State = TextFieldState;

    fn render(self, area: Rect, buf: &mut Buffer, state: &mut Self::State) {
        state.scroll.1 = state.scroll.1.min(state.cursor.1 as u16);
        state.scroll.0 = state.scroll.0.min(state.cursor.0 as u16);

        if state.cursor.0 as u16 >= state.scroll.0 + area.width {
            state.scroll.0 = state.cursor.0 as u16 - area.width + 1;
        }

        if state.cursor.1 as u16 >= state.scroll.1 + area.height {
            state.scroll.1 = state.cursor.1 as u16 - area.height + 1;
        }

        let para = Paragraph::new(
            state
                .lines
                .iter()
                .map(|s| Line::from(s.to_owned()))
                .collect::<Vec<_>>(),
        ) // (x, y) -> (y, x)
        .scroll((state.scroll.1, state.scroll.0));
        Widget::render(para, area, buf);
        state.cursor_screen_pos = (
            area.x + state.cursor.0 as u16 - state.scroll.0,
            area.y + state.cursor.1 as u16 - state.scroll.1,
        );
    }
}

impl EventHandler for TextFieldState {
    fn set_focus(&self, frame: &mut Frame) {
        let (x, y) = self.cursor_screen_pos;
        frame.set_cursor(x, y);
    }

    fn handle_event(&mut self, event: KeyEvent) -> Consumed {
        match event.code {
            KeyCode::Backspace => {
                if self.cursor.0 > 0 {
                    self.cursor.0 -= 1;
                    self.lines[self.cursor.1].remove_char(self.cursor.0);
                } else if self.cursor.1 > 0 && !self.lines.is_empty() {
                    let line = self.lines.remove(self.cursor.1);
                    self.cursor.1 -= 1;
                    self.cursor.0 = self.current_line_len();
                    if !self.lines.is_empty() {
                        self.lines[self.cursor.1].push_str(&line);
                    }
                }
            }
            KeyCode::Delete => {
                if !self.lines.is_empty() {
                    let line = self.lines.get_mut(self.cursor.1).unwrap();
                    if line.len() > self.cursor.0 {
                        line.remove_char(self.cursor.0);
                    } else if self.lines.len() > self.cursor.1 + 1 {
                        let line = self.lines.remove(self.cursor.1 + 1);
                        self.lines[self.cursor.1].push_str(&line);
                    }
                }
            }
            KeyCode::Enter => {
                if self.lines.is_empty() {
                    self.lines.push(String::new());
                    self.lines.push(String::new());
                    self.cursor.1 = 1;
                } else {
                    let line = &mut self.lines[self.cursor.1];
                    let split = line.split_at_char(self.cursor.0);
                    let split = (split.0.to_owned(), split.1.to_owned());
                    self.lines[self.cursor.1] = split.0;
                    self.cursor.1 += 1;
                    self.cursor.0 = 0;
                    self.lines.insert(self.cursor.1, split.1);
                }
            }
            KeyCode::Char(c) => {
                if self.lines.is_empty() {
                    self.lines.push(String::from(c));
                } else {
                    self.lines[self.cursor.1].insert_char(self.cursor.0, c);
                }
                self.cursor.0 += 1;
            }
            KeyCode::Up => {
                if self.cursor.1 > 0 {
                    self.cursor.1 -= 1;
                    self.cursor.0 = self.current_line_len().min(self.cursor.0);
                }
            }
            KeyCode::Down => {
                if self.cursor.1 + 1 < self.lines.len() {
                    self.cursor.1 += 1;
                    self.cursor.0 = self.current_line_len().min(self.cursor.0);
                }
            }
            KeyCode::Left => {
                if event.modifiers.intersects(KeyModifiers::CONTROL) {
                    self.cursor.0 = self.word_boundary_before();
                } else if self.cursor.0 > 0 {
                    self.cursor.0 -= 1;
                }
            }
            KeyCode::Right => {
                if event.modifiers.intersects(KeyModifiers::CONTROL) {
                    self.cursor.0 = self.word_boundary_after();
                } else {
                    self.cursor.0 = self.current_line_len().min(self.cursor.0 + 1);
                }
            }
            KeyCode::End => {
                self.cursor.0 = self.current_line_len();
            }
            KeyCode::Home => {
                self.cursor.0 = 0;
            }
            _ => (),
        };
        Consumed::Consumed
    }
}
