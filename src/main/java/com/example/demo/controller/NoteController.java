package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.Note;
import com.example.demo.service.note.NoteService;
import jakarta.annotation.Resource;
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
     * 获取所有笔记
     */
    @GetMapping("/list")
    public ApiResponse<List<Note>> getAllNotes() {
        List<Note> notes = noteService.getAllNotes();
        return ApiResponse.success(notes);
    }

    /**
     * 获取笔记详情
     */
    @GetMapping("/{id}")
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
    public ApiResponse<Note> createNote(@RequestBody Note note) {
        Note created = noteService.createNote(note);
        return ApiResponse.success(created);
    }

    /**
     * 更新笔记
     */
    @PutMapping("/{id}")
    public ApiResponse<Note> updateNote(@PathVariable Long id, @RequestBody Note note) {
        note.setId(id);
        Note updated = noteService.updateNote(note);
        return ApiResponse.success(updated);
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{id}")
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
    public ApiResponse<String> summarizeNote(@PathVariable Long id) {
        String summary = noteService.summarizeNote(id);
        if (summary == null) {
            return ApiResponse.error("笔记不存在或内容为空");
        }
        return ApiResponse.success(summary);
    }

    /**
     * 搜索笔记
     */
    @GetMapping("/search")
    public ApiResponse<List<Note>> searchNotes(@RequestParam(required = false) String keyword) {
        List<Note> notes = noteService.searchNotes(keyword);
        return ApiResponse.success(notes);
    }
}