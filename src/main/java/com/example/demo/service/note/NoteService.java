package com.example.demo.service.note;

import com.example.demo.entity.Note;
import com.example.demo.mapper.NoteMapper;
import com.example.demo.dto.NoteSemanticHit;
import dev.langchain4j.model.chat.ChatModel;
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
 * 笔记服务 - 文件存储模式
 * 笔记内容存储为 .md 文件，数据库只存储元数据和文件路径
 */
@Slf4j
@Service
public class NoteService {

    private static final String NOTES_DIR = "data/notes";

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private ChatModel chatModel;

    @Resource
    private NoteVectorService noteVectorService;

    @PostConstruct
    public void init() {
        // 初始化笔记存储目录
        try {
            Path notesPath = Paths.get(NOTES_DIR);
            if (!Files.exists(notesPath)) {
                Files.createDirectories(notesPath);
                log.info("创建笔记存储目录: {}", NOTES_DIR);
            }
        } catch (IOException e) {
            log.error("创建笔记存储目录失败: {}", e.getMessage());
        }
    }

    /**
     * 获取所有笔记
     */
    public List<Note> getAllNotes() {
        List<Note> notes = noteMapper.findAllOrderByPinnedAndTime();
        // 从文件读取内容
        for (Note note : notes) {
            loadContentFromFile(note);
        }
        return notes;
    }

    /**
     * 获取笔记详情
     */
    public Note getNote(Long id) {
        Note note = noteMapper.selectById(id);
        if (note != null) {
            loadContentFromFile(note);
        }
        return note;
    }

    /**
     * 创建笔记
     */
    @Transactional
    public Note createNote(Note note) {
        note.setCreateTime(LocalDateTime.now());
        note.setUpdateTime(LocalDateTime.now());
        if (note.getIsPinned() == null) {
            note.setIsPinned(false);
        }
        // 先插入数据库获取 ID
        noteMapper.insert(note);
        Long id = note.getId();

        // 创建文件并保存内容
        String filePath = NOTES_DIR + "/" + id + ".md";
        note.setFilePath(filePath);

        // 写入文件
        saveContentToFile(note);

        // 更新数据库中的文件路径
        noteMapper.updateById(note);
        noteVectorService.syncNote(note);
        log.info("创建笔记: id={}, filePath={}", note.getId(), filePath);
        return note;
    }

    /**
     * 更新笔记
     */
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
        // 更新文件内容
        saveContentToFile(note);
        // 更新数据库（只更新元数据，不更新 content）
        noteMapper.updateById(note);
        noteVectorService.syncNote(note);
        log.info("更新笔记: id={}", note.getId());
        return note;
    }

    /**
     * 删除笔记
     */
    @Transactional
    public boolean deleteNote(Long id) {
        Note note = noteMapper.selectById(id);
        if (note != null && note.getFilePath() != null) {
            // 删除文件
            try {
                Path filePath = Paths.get(note.getFilePath());
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.info("删除笔记文件: {}", note.getFilePath());
                }
            } catch (IOException e) {
                log.error("删除笔记文件失败: {}", e.getMessage());
            }
        }
        // 删除数据库记录
        int result = noteMapper.deleteById(id);
        if (result > 0) {
            noteVectorService.deleteNote(id);
        }
        log.info("删除笔记: id={}, result={}", id, result > 0);
        return result > 0;
    }

    /**
     * 切换置顶状态
     */
    @Transactional
    public Note togglePin(Long id) {
        Note note = noteMapper.selectById(id);
        if (note != null) {
            note.setIsPinned(!Boolean.TRUE.equals(note.getIsPinned()));
            note.setUpdateTime(LocalDateTime.now());
            noteMapper.updateById(note);
            // 加载内容
            loadContentFromFile(note);
        }
        return note;
    }

    /**
     * AI 总结笔记
     */
    public Note summarizeNote(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null) {
            return null;
        }
        // 从文件读取内容
        loadContentFromFile(note);
        if (note.getContent() == null || note.getContent().isEmpty()) {
            return null;
        }

        String prompt = "请对以下笔记内容进行总结，用1-2句话概括要点：\n\n" + note.getContent();
        String summary = chatModel.chat(prompt);

        // 保存摘要到数据库
        note.setAiSummary(summary);
        note.setUpdateTime(LocalDateTime.now());
        noteMapper.updateById(note);
        noteVectorService.syncNote(note);

        return note;
    }

    /**
     * 搜索笔记
     */
    public List<Note> searchNotes(String keyword) {
        List<Note> allNotes = getAllNotes();
        if (keyword == null || keyword.isEmpty()) {
            return allNotes;
        }
        String lowerKeyword = keyword.toLowerCase();
        return allNotes.stream()
                .filter(n -> (n.getTitle() != null && n.getTitle().toLowerCase().contains(lowerKeyword))
                        || (n.getContent() != null && n.getContent().toLowerCase().contains(lowerKeyword))
                        || (n.getTags() != null && n.getTags().toLowerCase().contains(lowerKeyword)))
                .toList();
    }

    public int reindexAllNotes() {
        List<Note> notes = noteMapper.findAllOrderByPinnedAndTime();
        notes.forEach(this::loadContentFromFile);
        notes.forEach(noteVectorService::syncNote);
        return notes.size();
    }

    public List<NoteSemanticHit> semanticSearch(String query, int topK) {
        return noteVectorService.search(query, topK);
    }

    /**
     * 从文件加载笔记内容
     */
    private void loadContentFromFile(Note note) {
        if (note.getFilePath() != null) {
            try {
                Path filePath = Paths.get(note.getFilePath());
                if (Files.exists(filePath)) {
                    String content = Files.readString(filePath);
                    note.setContent(content);
                } else {
                    note.setContent("");
                }
            } catch (IOException e) {
                log.error("读取笔记文件失败: id={}, filePath={}, error={}",
                    note.getId(), note.getFilePath(), e.getMessage());
                note.setContent("");
            }
        } else {
            note.setContent("");
        }
    }

    /**
     * 保存笔记内容到文件
     */
    private void saveContentToFile(Note note) {
        if (note.getFilePath() == null) {
            log.warn("笔记 filePath 为空，跳过文件保存: id={}", note.getId());
            return;
        }
        try {
            Path filePath = Paths.get(note.getFilePath());
            String content = note.getContent() != null ? note.getContent() : "";
            Files.writeString(filePath, content);
            log.debug("保存笔记文件: filePath={}, size={} bytes", note.getFilePath(), content.length());
        } catch (IOException e) {
            log.error("保存笔记文件失败: id={}, filePath={}, error={}",
                note.getId(), note.getFilePath(), e.getMessage());
        }
    }
}
