package com.example.demo.note.web;

import com.example.demo.infrastructure.config.CacheConfig;
import com.example.demo.note.application.NoteService;
import com.example.demo.note.domain.Note;
import com.example.demo.shared.dto.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 笔记业务控制器。
 *
 * <p>Java 仅提供笔记 CRUD 与关键词搜索。AI 总结和语义检索由 Python Agent Engine 提供。</p>
 */
@RestController
@RequestMapping("/api/note")
public class NoteController {

    @Resource
    private NoteService noteService;

    /** 查询全部笔记。 */
    @GetMapping("/list")
    @Cacheable(cacheNames = CacheConfig.NOTE_LIST, sync = true)
    public ApiResponse<List<Note>> getAllNotes() {
        return ApiResponse.success(noteService.getAllNotes());
    }

    /** 查询笔记详情。 */
    @GetMapping("/{id}")
    @Cacheable(cacheNames = CacheConfig.NOTE_DETAIL, key = "#id", sync = true)
    public ApiResponse<Note> getNote(@PathVariable Long id) {
        Note note = noteService.getNote(id);
        return note == null ? ApiResponse.error("笔记不存在") : ApiResponse.success(note);
    }

    /** 创建笔记。 */
    @PostMapping
    @CacheEvict(cacheNames = CacheConfig.NOTE_LIST, allEntries = true)
    public ApiResponse<Note> createNote(@RequestBody Note note) {
        return ApiResponse.success(noteService.createNote(note));
    }

    /** 更新笔记。 */
    @PutMapping("/{id}")
    @CacheEvict(cacheNames = {CacheConfig.NOTE_LIST, CacheConfig.NOTE_DETAIL}, allEntries = true)
    public ApiResponse<Note> updateNote(@PathVariable Long id, @RequestBody Note note) {
        note.setId(id);
        Note updated = noteService.updateNote(note);
        return updated == null ? ApiResponse.error("笔记不存在") : ApiResponse.success(updated);
    }

    /** 删除笔记。 */
    @DeleteMapping("/{id}")
    @CacheEvict(cacheNames = {CacheConfig.NOTE_LIST, CacheConfig.NOTE_DETAIL}, allEntries = true)
    public ApiResponse<Void> deleteNote(@PathVariable Long id) {
        return noteService.deleteNote(id) ? ApiResponse.success(null) : ApiResponse.error("删除失败");
    }

    /** 切换置顶状态。 */
    @PutMapping("/{id}/pin")
    @CacheEvict(cacheNames = {CacheConfig.NOTE_LIST, CacheConfig.NOTE_DETAIL}, allEntries = true)
    public ApiResponse<Note> togglePin(@PathVariable Long id) {
        Note note = noteService.togglePin(id);
        return note == null ? ApiResponse.error("笔记不存在") : ApiResponse.success(note);
    }

    /** 使用关键词搜索笔记。 */
    @GetMapping("/search")
    public ApiResponse<List<Note>> searchNotes(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(noteService.searchNotes(keyword));
    }
}
