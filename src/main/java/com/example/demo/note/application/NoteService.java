package com.example.demo.note.application;

import com.example.demo.note.domain.Note;
import com.example.demo.note.persistence.NoteMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记业务服务。
 *
 * <p>Java 仅负责笔记文件与元数据 CRUD。AI 总结、Embedding 和语义检索已经迁移到 Python Agent
 * Engine。</p>
 */
@Slf4j
@Service
public class NoteService {

    private static final String NOTES_DIR = "data/notes";

    @Resource
    private NoteMapper noteMapper;

    /** 初始化笔记存储目录。 */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(NOTES_DIR));
        } catch (IOException error) {
            log.error("创建笔记存储目录失败: {}", error.getMessage());
        }
    }

    /** 查询全部笔记。 */
    public List<Note> getAllNotes() {
        List<Note> notes = noteMapper.findAllOrderByPinnedAndTime();
        notes.forEach(this::loadContentFromFile);
        return notes;
    }

    /** 根据 ID 查询笔记。 */
    public Note getNote(Long id) {
        Note note = noteMapper.selectById(id);
        if (note != null) {
            loadContentFromFile(note);
        }
        return note;
    }

    /** 创建笔记。 */
    @Transactional
    public Note createNote(Note note) {
        LocalDateTime now = LocalDateTime.now();
        note.setCreateTime(now);
        note.setUpdateTime(now);
        if (note.getIsPinned() == null) {
            note.setIsPinned(false);
        }
        noteMapper.insert(note);
        note.setFilePath(NOTES_DIR + "/" + note.getId() + ".md");
        saveContentToFile(note);
        noteMapper.updateById(note);
        return note;
    }

    /** 从备份恢复笔记。 */
    @Transactional
    public Note restoreNote(Note note) {
        if (note == null) {
            return null;
        }
        Note restored = Note.builder()
                .title(note.getTitle())
                .content(note.getContent())
                .tags(note.getTags())
                .aiSummary(note.getAiSummary())
                .isPinned(Boolean.TRUE.equals(note.getIsPinned()))
                .createTime(note.getCreateTime() != null ? note.getCreateTime() : LocalDateTime.now())
                .updateTime(note.getUpdateTime() != null ? note.getUpdateTime() : LocalDateTime.now())
                .build();
        noteMapper.insert(restored);
        restored.setFilePath(NOTES_DIR + "/" + restored.getId() + ".md");
        saveContentToFile(restored);
        noteMapper.updateById(restored);
        return restored;
    }

    /** 更新笔记。 */
    @Transactional
    public Note updateNote(Note note) {
        Note existing = noteMapper.selectById(note.getId());
        if (existing == null) {
            return null;
        }
        note.setFilePath(existing.getFilePath());
        note.setCreateTime(existing.getCreateTime());
        note.setUpdateTime(LocalDateTime.now());
        if (note.getTags() == null) {
            note.setTags(existing.getTags());
        }
        if (note.getAiSummary() == null) {
            note.setAiSummary(existing.getAiSummary());
        }
        if (note.getIsPinned() == null) {
            note.setIsPinned(existing.getIsPinned());
        }
        saveContentToFile(note);
        noteMapper.updateById(note);
        return note;
    }

    /** 删除笔记。 */
    @Transactional
    public boolean deleteNote(Long id) {
        Note note = noteMapper.selectById(id);
        if (note != null && note.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(note.getFilePath()));
            } catch (IOException error) {
                log.warn("删除笔记文件失败: id={}, reason={}", id, error.getMessage());
            }
        }
        return noteMapper.deleteById(id) > 0;
    }

    /** 切换置顶状态。 */
    @Transactional
    public Note togglePin(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            return null;
        }
        note.setIsPinned(!Boolean.TRUE.equals(note.getIsPinned()));
        note.setUpdateTime(LocalDateTime.now());
        noteMapper.updateById(note);
        loadContentFromFile(note);
        return note;
    }

    /** 使用关键词搜索笔记。 */
    public List<Note> searchNotes(String keyword) {
        List<Note> notes = getAllNotes();
        if (keyword == null || keyword.isBlank()) {
            return notes;
        }
        String normalized = keyword.toLowerCase();
        return notes.stream()
                .filter(note -> contains(note.getTitle(), normalized)
                        || contains(note.getContent(), normalized)
                        || contains(note.getTags(), normalized))
                .toList();
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private void loadContentFromFile(Note note) {
        if (note.getFilePath() == null) {
            note.setContent("");
            return;
        }
        try {
            Path path = Paths.get(note.getFilePath());
            note.setContent(Files.exists(path) ? Files.readString(path) : "");
        } catch (IOException error) {
            log.warn("读取笔记文件失败: id={}, reason={}", note.getId(), error.getMessage());
            note.setContent("");
        }
    }

    private void saveContentToFile(Note note) {
        if (note.getFilePath() == null) {
            return;
        }
        try {
            Path path = Paths.get(note.getFilePath());
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, note.getContent() == null ? "" : note.getContent());
        } catch (IOException error) {
            throw new IllegalStateException("保存笔记文件失败", error);
        }
    }
}
