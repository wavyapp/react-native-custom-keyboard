
#import <Foundation/Foundation.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTRootView.h>

@interface RNCustomKeyboard : NSObject <RCTBridgeModule>
@property (nonatomic) NSMutableDictionary * dicInputMaxLength;
@property (nonatomic) RCTRootView * inputView;
@end
  
