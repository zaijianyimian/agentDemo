package com.example.demo.note.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.infrastructure.config.CacheConfig;
import com.example.demo.note.dto.NoteSemanticHit;
import com.example.demo.note.domain.Note;
import com.example.demo.note.application.NoteService;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 笔记控制器
 */
@RestController
@RequestMapping("/api/note")
public class NoteController {

    @Resource
    private NoteService noteService;

    /**
     * 获取所有笔记。30 秒 Caffeine 缓存，sync=true 让并发同 key 请求只查一次 DB。
     */
    @GetMapping("/list")
    @Cacheable(cacheNames = CacheConfig.NOTE_LIST, sync = true)
    public ApiResponse<List<Note>> getAllNotes() {
        List<Note> notes = noteService.getAllNotes();
        return ApiResponse.success(notes);
    }

    /**
     * 获取笔记详情
     */
    @GetMapping("/{id}")
    @Cacheable(cacheNames = CacheConfig.NOTE_DETAIL, key = "#id", sync = true)
    public ApiResponse<Note> getNote(@PathVariable Long id) {
        Note note = noteService.getNote(id);
        if (note == null) {
            return ApiResponse.error("笔记不存在");
        }
        return ApiResponse.success(note);
    }

    /**
     * 创建笔记
     */
    @PostMapping
    @CacheEvict(cacheNames = CacheConfig.NOTE_LIST, allEntries = true)
    public ApiResponse<Note> createNote(@RequestBody Note note) {
        Note created = noteService.createNote(note);
        return ApiResponse.success(created);
    }

    /**
     * 更新笔记
     */
    @PutMapping("/{id}")
    @CacheEvict(cacheNames = {CacheConfig.NOTE_LIST, CacheConfig.NOTE_DETAIL}, allEntries = true)
    public ApiResponse<Note> updateNote(@PathVariable Long id, @RequestBody Note note) {
        note.setId(id);
        Note updated = noteService.updateNote(note);
        if (updated == null) {
            return ApiResponse.error("笔记不存在");
        }
        return ApiResponse.success(updated);
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{id}")
    @CacheEvict(cacheNames = {CacheConfig.NOTE_LIST, CacheConfig.NOTE_DETAIL}, allEntries = true)
    public ApiResponse<Void> deleteNote(@PathVariable Long id) {
        boolean result = noteService.deleteNote(id);
        if (!result) {
            return ApiResponse.error("删除失败");
        }
        return ApiResponse.success(null);
    }

    /**
     * 切换置顶状态
     */
    @PutMapping("/{id}/pin")
    @CacheEvict(cacheNames = {CacheConfig.NOTE_LIST, CacheConfig.NOTE_DETAIL}, allEntries = true)
    public ApiResponse<Note> togglePin(@PathVariable Long id) {
        Note note = noteService.togglePin(id);
        if (note == null) {
            return ApiResponse.error("笔记不存在");
        }
        return ApiResponse.success(note);
    }

    /**
     * AI 总结笔记
     */
    @PostMapping("/{id}/summarize")
    @CacheEvict(cacheNames = {CacheConfig.NOTE_LIST, CacheConfig.NOTE_DETAIL}, allEntries = true)
    public ApiResponse<Note> summarizeNote(@PathVariable Long id) {
        Note note = noteService.summarizeNote(id);
        if (note == null) {
            return ApiResponse.error("笔记不存在或内容为空");
        }
        return ApiResponse.success(note);
    }

    /**
     * 搜索笔记
     */
    @GetMapping("/search")
    public ApiResponse<List<Note>> searchNotes(@RequestParam(required = false) String keyword) {
        List<Note> notes = noteService.searchNotes(keyword);
        return ApiResponse.success(notes);
    }

    /**
     * 重新索引全部笔记
     */
    @PostMapping("/reindex")
    @CacheEvict(cacheNames = CacheConfig.NOTE_LIST, allEntries = true)
    public ApiResponse<Integer> reindexNotes() {
        return ApiResponse.success(noteService.reindexAllNotes());
    }

    /**
     * 笔记语义检索
     */
    @GetMapping("/semantic-search")
    public ApiResponse<List<NoteSemanticHit>> semanticSearch(@RequestParam String query,
                                                             @RequestParam(defaultValue = "5") int topK) {
        return ApiResponse.success(noteService.semanticSearch(query, topK));
    }
}
