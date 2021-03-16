
#import "RNCustomKeyboard.h"
#import <React/RCTBridge+Private.h>
#import <React/RCTUIManager.h>
#import <RCTText/RCTBaseTextInputView.h>
#import <AudioToolbox/AudioToolbox.h>

@implementation RNCustomKeyboard

@synthesize bridge = _bridge;

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE(CustomKeyboard)

RCT_EXPORT_METHOD(install:(nonnull NSNumber *)reactTag withType:(nonnull NSString *)keyboardType maxLength:(int) maxLength inputViewHeight:(int) inputViewHeight)
{
    RCTRootView * _inputView = [[RCTRootView alloc] initWithBridge:((RCTBridge *)_bridge).parentBridge moduleName:@"CustomKeyboard" initialProperties:
                      @{
                        @"tag": reactTag,
                        @"type": keyboardType
                        }
                      ];

    if (_dicInputMaxLength == nil) {
        _dicInputMaxLength = [NSMutableDictionary dictionaryWithCapacity:0];
    }
    
    [_dicInputMaxLength setValue:[NSNumber numberWithInt:maxLength] forKey:[reactTag stringValue]];
    
    _inputView.autoresizingMask = UIViewAutoresizingNone;
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);

    //* temporarily solution for duplicating default text
    //* the first call of "insertText" when default text is existed, default text will be copied after inserting text.
    //* I couldn't find exactly reason but this protects duplicating text.
    [view replaceRange:view.selectedTextRange withText:@""];

    _inputView.frame = CGRectMake(0, 0, [UIScreen mainScreen].bounds.size.width, inputViewHeight);
    _inputView.backgroundColor = [UIColor colorWithRed:0 green:0 blue:0 alpha:0];
    
    view.inputView = _inputView;
    [view reloadInputViews];
}

RCT_EXPORT_METHOD(uninstall:(nonnull NSNumber *)reactTag)
{
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    
    view.inputView = nil;
    [view reloadInputViews];
}

- (UITextView*)getRealTextView:(UITextView*)reactView {
    if ([self canInputText:reactView]) {
        return reactView;
    }
    // RN 0.50 后 RCTTextField 不是继承自 UITextField 了，多包了一层，这里遍历一下去查找
    for (UITextView *aView in reactView.subviews) {
        if ([self canInputText:aView]) {
            ((UITextView*)aView).inputView = nil;
        }
    }
    return nil;
}
- (BOOL)canInputText:(UIView*)view {
    return [view isKindOfClass:[UITextField class]] || [view isKindOfClass:[UITextView class]];
}

RCT_EXPORT_METHOD(getSelectionRange:(nonnull NSNumber *)reactTag callback:(RCTResponseSenderBlock)callback) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    UITextRange* range = view.selectedTextRange;
    
    const NSInteger start = [view offsetFromPosition:view.beginningOfDocument toPosition:range.start];
    const NSInteger end = [view offsetFromPosition:view.beginningOfDocument toPosition:range.end];
    callback(@[@{@"text":view.text, @"start":[NSNumber numberWithInteger:start], @"end":[NSNumber numberWithInteger:end]}]);
}

RCT_EXPORT_METHOD(submitEditing:(nonnull NSNumber *)reactTag withText:(NSString*)text) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    AudioServicesPlaySystemSound(1104);
    [_bridge.eventDispatcher sendTextEventWithType:RCTTextEventTypeSubmit
                                reactTag:reactTag
                                text:view.attributedText.string
                                key:nil
                                eventCount:0];

}


RCT_EXPORT_METHOD(insertKey:(nonnull NSNumber *)reactTag withText:(NSString*)text) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    AudioServicesPlaySystemSound(1104);
    [_bridge.eventDispatcher sendTextEventWithType:RCTTextEventTypeKeyPress
                                reactTag:reactTag
                                text:view.attributedText.string
                                key:text
                                eventCount:0];

}


RCT_EXPORT_METHOD(insertText:(nonnull NSNumber *)reactTag withText:(NSString*)text) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    if (_dicInputMaxLength != nil) {
        NSString *textValue = [NSString stringWithFormat:@"%@", view.text];
        int  maxLegth = [_dicInputMaxLength[reactTag.stringValue] intValue];
        if ([textValue length] >= maxLegth) {
            return;
        }
    }
    [view replaceRange:view.selectedTextRange withText:text];
}

RCT_EXPORT_METHOD(backSpace:(nonnull NSNumber *)reactTag) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    
    UITextRange* range = view.selectedTextRange;
    if ([view comparePosition:range.start toPosition:range.end] == 0) {
        range = [view textRangeFromPosition:[view positionFromPosition:range.start offset:-1] toPosition:range.start];
    }
    [view replaceRange:range withText:@""];
}

RCT_EXPORT_METHOD(doDelete:(nonnull NSNumber *)reactTag) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    
    UITextRange* range = view.selectedTextRange;
    if ([view comparePosition:range.start toPosition:range.end] == 0) {
        range = [view textRangeFromPosition:range.start toPosition:[view positionFromPosition: range.start offset: 1]];
    }
    [view replaceRange:range withText:@""];
}

RCT_EXPORT_METHOD(moveLeft:(nonnull NSNumber *)reactTag) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    
    UITextRange* range = view.selectedTextRange;
    UITextPosition* position = range.start;
    
    if ([view comparePosition:range.start toPosition:range.end] == 0) {
        position = [view positionFromPosition: position offset: -1];
    }
    
    view.selectedTextRange = [view textRangeFromPosition: position toPosition:position];
}

RCT_EXPORT_METHOD(moveRight:(nonnull NSNumber *)reactTag) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    
    UITextRange* range = view.selectedTextRange;
    UITextPosition* position = range.end;
    
    if ([view comparePosition:range.start toPosition:range.end] == 0) {
        position = [view positionFromPosition: position offset: 1];
    }
    
    view.selectedTextRange = [view textRangeFromPosition: position toPosition:position];
}

RCT_EXPORT_METHOD(switchSystemKeyboard:(nonnull NSNumber*) reactTag) {
    UITextView *view = (UITextView *)(((RCTBaseTextInputView*)[_bridge.uiManager viewForReactTag:reactTag]).backedTextInputView);
    UIView* inputView = view.inputView;
    view.inputView = nil;
    [view reloadInputViews];
    view.inputView = inputView;
}

@end

