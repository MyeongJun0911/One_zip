package com.sh.onezip.tip.controller;

import com.sh.onezip.attachment.dto.AttachmentCreateDto;
import com.sh.onezip.attachment.dto.AttachmentDetailDto;
import com.sh.onezip.attachment.service.AttachmentService;
import com.sh.onezip.attachment.service.S3FileService;
import com.sh.onezip.auth.vo.MemberDetails;
import com.sh.onezip.tip.dto.TipCreateDto;
import com.sh.onezip.tip.dto.TipDetailDto;
import com.sh.onezip.tip.dto.TipListDto;
import com.sh.onezip.tip.entity.TipComment;
import com.sh.onezip.tip.service.TipCommentService;
import com.sh.onezip.tip.service.TipService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

@Controller
@Slf4j
public class TipController {
    @Autowired
    private TipService tipService;
    @Autowired
    private S3FileService s3FileService;
    @Autowired
    AttachmentService attachmentService;
    @Autowired
    private TipCommentService tipCommentService;


    @GetMapping("/tipList.do")
    public void boardList(@PageableDefault(size = 5, page = 0) Pageable pageable, Model model) {
        log.info("tipService={}",tipService.getClass());

        log.debug("pageable = {}", pageable);
        Page<TipListDto> tipPage = tipService.findAll(pageable);
        log.debug("tips = {}", tipPage.getContent());
        model.addAttribute("tips", tipPage.getContent());
        model.addAttribute("totalCount", tipPage.getTotalElements()); // 전체 게시물수
    }

    @PostMapping("/createTip.do")
    public String createTip(
            @Valid TipCreateDto tipCreateDto,
            BindingResult bindingResult,
            @RequestParam("upFile") List<MultipartFile> upFiles,
            @AuthenticationPrincipal MemberDetails memberDetails,
            RedirectAttributes redirectAttributes)
            throws IOException {
        if (bindingResult.hasErrors()) {
            throw new RuntimeException(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        // 첨부파일 S3에 저장
        for (MultipartFile upFile : upFiles) {
            if (upFile.getSize() > 0) {
                AttachmentCreateDto attachmentCreateDto = s3FileService.upload(upFile);
                log.debug("attachmentCreateDto = {}", attachmentCreateDto);
                tipCreateDto.addAttachmentCreateDto(attachmentCreateDto);
            }
        }

        // DB에 저장(게시글, 첨부파일)
        tipCreateDto.setMemberId(memberDetails.getMember().getMemberId());
        tipService.createTip(tipCreateDto);

        // 리다이렉트후에 사용자피드백
        redirectAttributes.addFlashAttribute("msg", "🎈🎈🎈 게시글을 성공적으로 등록했습니다. 🎈🎈🎈");
        return "redirect:/board/boardList.do";
    }

    @GetMapping("/tipDetail.do")
    public void tipDetail(@RequestParam("id") Long id, Model model) {
        TipDetailDto tipDetailDto = tipService.findById(id);
        model.addAttribute("tip", tipDetailDto);
        log.debug("tip = {}", tipDetailDto);
        throw new RuntimeException("zzzzzz");
    }

    @GetMapping("/fileDownload.do")
    public ResponseEntity<?> fileDownload(@RequestParam("id") Long id) throws UnsupportedEncodingException {
        AttachmentDetailDto attachmentDetailDto = attachmentService.findById(id);

        return s3FileService.download(attachmentDetailDto);
    }

    @PostMapping("/createComment.do")
    public String createComment(
            @Valid TipComment tipComment,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            throw new RuntimeException(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        tipComment.setMember(memberDetails.getMember());
        tipCommentService.createComment(tipComment);
        redirectAttributes.addFlashAttribute("msg", "댓글을 성공적으로 등록했습니다.");
        return "redirect:/tipDetail.do?id=" + tipComment.getTip().getId(); // 댓글을 단 팁 상세보기 페이지로 리다이렉트
    }

    @PostMapping("/deleteComment.do")
    public String deleteComment(@RequestParam("commentId") Long commentId,
                                @RequestParam("tipId") Long tipId,
                                RedirectAttributes redirectAttributes) {
        tipCommentService.deleteComment(commentId);
        redirectAttributes.addFlashAttribute("msg", "댓글을 성공적으로 삭제했습니다.");
        return "redirect:/tipDetail.do?id=" + tipId; // 댓글이 삭제된 후의 팁 상세보기 페이지로 리다이렉트
    }

}
